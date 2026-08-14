let appConfig = { onlyofficeUrl: '', publicUrl: '' };
let editorInstance = null;
let docsApiReady = false;

document.addEventListener('DOMContentLoaded', init);

async function init() {
    document.getElementById('file-input').addEventListener('change', handleUpload);
    document.getElementById('editor-close').addEventListener('click', closeEditor);

    try {
        appConfig = await fetchJson('/api/config');
        setStatus('已连接');
        await loadDocsApi();
        await refreshList();
    } catch (error) {
        setStatus('连接失败');
        showToast(error.message || '无法连接文档服务');
    }
}

async function loadDocsApi() {
    if (window.DocsAPI) {
        docsApiReady = true;
        return;
    }
    return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = appConfig.onlyofficeUrl + '/web-apps/apps/api/documents/api.js';
        script.onload = () => {
            docsApiReady = true;
            resolve();
        };
        script.onerror = () => reject(new Error('OnlyOffice SDK load failed: ' + script.src));
        document.head.appendChild(script);
    });
}

async function refreshList() {
    const documents = await fetchJson('/api/documents');
    renderDocuments(documents);
}

function renderDocuments(documents) {
    const tbody = document.getElementById('document-list');
    const empty = document.getElementById('empty');
    const count = document.getElementById('doc-count');
    count.textContent = documents.length;
    tbody.innerHTML = '';

    if (documents.length === 0) {
        empty.classList.remove('hidden');
        return;
    }
    empty.classList.add('hidden');

    for (const doc of documents) {
        const tr = document.createElement('tr');
        tr.appendChild(tdText(escapeHtml(doc.filename), 'filename'));
        tr.appendChild(tdText(formatSize(doc.size)));
        tr.appendChild(tdText(formatTime(doc.uploadedAt)));

        const actionCell = document.createElement('td');
        const actions = document.createElement('div');
        actions.className = 'actions';

        actions.appendChild(actionButton('预览', () => openEditor(doc, 'preview')));
        actions.appendChild(actionButton('编辑', () => openEditor(doc, 'edit')));
        actions.appendChild(actionButton('转 PDF', (event) => convertToPdf(doc, event)));

        const pdfButton = actionButton(doc.pdfUrl ? '下载 PDF' : 'PDF', () => downloadPdf(doc));
        pdfButton.disabled = !doc.pdfUrl;
        actions.appendChild(pdfButton);
        actions.appendChild(actionButton('删除', () => deleteDocument(doc), 'button-danger'));

        actionCell.appendChild(actions);
        tr.appendChild(actionCell);
        tbody.appendChild(tr);
    }
}

function actionButton(label, onClick, extraClass) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'button' + (extraClass ? ' ' + extraClass : '');
    button.textContent = label;
    button.addEventListener('click', onClick);
    return button;
}

function tdText(text, className) {
    const td = document.createElement('td');
    td.textContent = text;
    if (className) {
        td.className = className;
    }
    return td;
}

async function handleUpload(event) {
    const input = event.target;
    const file = input.files && input.files[0];
    if (!file) {
        return;
    }
    const status = document.getElementById('upload-status');
    status.textContent = file.name + ' 上传中';
    try {
        const form = new FormData();
        form.append('file', file);
        const response = await fetch('/api/documents/upload', { method: 'POST', body: form });
        if (!response.ok) {
            const error = await readError(response);
            throw new Error(error);
        }
        const doc = await response.json();
        status.textContent = file.name + ' 上传完成';
        await refreshList();
    } catch (error) {
        status.textContent = file.name + ' 上传失败';
        showToast(error.message);
    } finally {
        input.value = '';
    }
}

async function openEditor(doc, mode) {
    if (!docsApiReady) {
        try {
            await loadDocsApi();
        } catch (error) {
            showToast(error.message || '无法加载 OnlyOffice 前端 SDK');
            return;
        }
    }
    try {
        const editorConfig = await fetchJson('/api/documents/' + doc.id + '/' + mode);
        document.getElementById('editor-title').textContent = doc.filename;
        document.getElementById('editor-overlay').classList.remove('hidden');
        const placeholder = document.getElementById('editor-placeholder');
        placeholder.innerHTML = '';
        if (editorInstance) {
            try {
                editorInstance.destroyEditor();
            } catch (error) {
                // ignore stale editor instance
            }
            editorInstance = null;
        }
        editorInstance = new DocsAPI.DocEditor('editor-placeholder', editorConfig);
    } catch (error) {
        showToast(error.message || '无法打开文档编辑器');
    }
}

function closeEditor() {
    if (editorInstance) {
        try {
            editorInstance.destroyEditor();
        } catch (error) {
            // ignore stale editor instance
        }
        editorInstance = null;
    }
    document.getElementById('editor-placeholder').innerHTML = '';
    document.getElementById('editor-overlay').classList.add('hidden');
    refreshList();
}

async function convertToPdf(doc, event) {
    const button = event && event.target;
    if (button) {
        button.disabled = true;
        button.textContent = '转换中';
    }
    try {
        const response = await fetch('/api/documents/' + doc.id + '/convert/pdf', { method: 'POST' });
        if (!response.ok) {
            const error = await readError(response);
            throw new Error(error);
        }
        await response.json();
        showToast('PDF 转换完成');
        await refreshList();
    } catch (error) {
        showToast('PDF 转换失败：' + error.message);
    } finally {
        if (button) {
            button.disabled = false;
            button.textContent = '转 PDF';
        }
    }
}

function downloadPdf(doc) {
    window.location.href = doc.pdfUrl;
}

async function deleteDocument(doc) {
    if (!window.confirm('确定删除 ' + doc.filename + ' ？')) {
        return;
    }
    try {
        const response = await fetch('/api/documents/' + doc.id, { method: 'DELETE' });
        if (!response.ok) {
            const error = await readError(response);
            throw new Error(error);
        }
        await refreshList();
    } catch (error) {
        showToast('删除失败：' + error.message);
    }
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        const error = await readError(response);
        throw new Error(error);
    }
    return response.json();
}

async function readError(response) {
    try {
        const body = await response.json();
        return body.message || response.statusText;
    } catch (error) {
        return response.statusText;
    }
}

function setStatus(text) {
    document.getElementById('connection-status').textContent = text;
}

function showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.remove('hidden');
    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => toast.classList.add('hidden'), 3000);
}

function formatSize(bytes) {
    if (bytes < 1024) {
        return bytes + ' B';
    }
    if (bytes < 1024 * 1024) {
        return (bytes / 1024).toFixed(1) + ' KB';
    }
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function formatTime(value) {
    if (!value) {
        return '-';
    }
    const date = new Date(value);
    const pad = (n) => String(n).padStart(2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
        + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value;
    return div.innerHTML;
}
