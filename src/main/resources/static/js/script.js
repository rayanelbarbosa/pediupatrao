// Alternar sidebar em telas pequenas
document.querySelector('.toggle-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('active');
});

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}

// ========================
// Utilitarios para chamadas a API
// ========================

// Cabecalhos com o token CSRF (as paginas expoem o token em meta tags)
function cabecalhosApi(comJson) {
    const token = document.querySelector('meta[name="_csrf"]');
    const nome = document.querySelector('meta[name="_csrf_header"]');
    const cabecalhos = {};
    if (token && nome) {
        cabecalhos[nome.content] = token.content;
    }
    if (comJson) {
        cabecalhos['Content-Type'] = 'application/json';
    }
    return cabecalhos;
}

// Envia a requisicao e devolve { ok, dados }. A mensagem de erro da API vem em dados.mensagem
async function chamarApi(url, metodo, corpo) {
    const opcoes = { method: metodo, headers: cabecalhosApi(corpo !== undefined) };
    if (corpo !== undefined) {
        opcoes.body = JSON.stringify(corpo);
    }
    const resposta = await fetch(url, opcoes);
    let dados = null;
    try {
        dados = await resposta.json();
    } catch (e) {
        // resposta sem corpo (ex.: 204)
    }
    return { ok: resposta.ok, status: resposta.status, dados: dados };
}

function escaparHtml(texto) {
    return String(texto ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}
