const $ = id => document.getElementById(id);
let collection, latest, activeTab = 'postings', searchSequence = 0, autoSequence = 0, spellSequence = 0;
let suggestionIndex = -1, autoTimer, toastTimer, collectionBusy = false;
const number = value => new Intl.NumberFormat().format(value);
const node = (tag, className, text) => {
  const item = document.createElement(tag);
  if (className) item.className = className;
  if (text !== undefined) item.textContent = text;
  return item;
};
async function api(path, values) {
  const response = await fetch(path, values === undefined ? {} : {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    body: new URLSearchParams(values)
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'The request could not be completed.');
  return data;
}
function error(message) {
  $('search-error').textContent = message;
  $('search-error').hidden = !message;
}
function toast(message) {
  clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false;
  toastTimer = setTimeout(() => $('toast').hidden = true, 4000);
}
function ready() {
  latest = null; searchSequence++; closeSuggestions(); error('');
  $('results-meta').textContent = 'Ready when you are';
  const empty = node('div','empty-state');
  empty.append(node('div','empty-symbol','⌕'),node('h3','','Your collection is ready'),
    node('p','','Enter a query or choose an example to find documents ranked by BM25 relevance.'));
  const flow = node('div','empty-flow');
  for (const word of ['Query','→','Match','→','Rank']) flow.append(node('span','',word));
  empty.append(flow); $('results').replaceChildren(empty); renderInspector();
}
function renderStatus(data) {
  collection = data;
  $('stat-documents').textContent = number(data.documents);
  $('stat-terms').textContent = number(data.terms);
  $('stat-tokens').textContent = number(data.tokens);
  $('collection-name').textContent = data.collection;
  $('collection-caption').textContent = `${number(data.documents)} documents in memory`;
  $('connection-label').textContent = 'Local Java engine connected';
  const list = $('file-list'); list.replaceChildren();
  for (const file of data.files.slice(0,8)) {
    const item = node('button','',file.title); item.title = file.title;
    item.addEventListener('click',() => openDocument(file.id)); list.append(item);
  }
  if (data.documents > 8) list.append(node('span','more-files',`+ ${data.documents - 8} more documents`));
  const examples = data.collection.startsWith('RFC') ? ['connection','"domain name"','(connection OR datagram) NOT header']
    : data.collection.startsWith('Demo') ? ['data structures','"machine learning"','NOT android']
    : data.files.length ? [data.files[0].title.replace(/\.(txt|md)$/i,'').split(/[^\p{L}\p{N}]+/u).filter(Boolean)[0] || 'document'] : [];
  $('example-queries').replaceChildren(node('span','','Try a query'));
  for (const query of examples) {
    const button = node('button','',query); button.type = 'button';
    button.addEventListener('click',() => { $('query').value = query; $('search-form').requestSubmit(); });
    $('example-queries').append(button);
  }
}

async function search(event) {
  event.preventDefault();
  if (collectionBusy) return;
  const query = $('query').value.trim();
  if (!query) return;
  const sequence = ++searchSequence;
  closeSuggestions(); error(''); latest = null; renderInspector();
  $('search-button').disabled = true; $('results-meta').textContent = 'Searching…';
  $('results').replaceChildren(node('div','loading-state','Evaluating your query…'));
  try {
    const data = await api('/api/search',{query,k:$('top-k').value});
    if (sequence !== searchSequence) return;
    latest = data; renderResults(); renderInspector();
  } catch (ex) {
    if (sequence !== searchSequence) return;
    $('results-meta').textContent = 'Check your query';
    const empty = node('div','empty-state');
    empty.append(node('h3','','This query needs a small correction'),node('p','',ex.message));
    $('results').replaceChildren(empty); error(ex.message);
  } finally { if (sequence === searchSequence) $('search-button').disabled = false; }
}
$('search-form').addEventListener('submit',search);
$('top-k').addEventListener('change',() => { if (latest) $('search-form').requestSubmit(); });
function renderResults() {
  $('results-meta').textContent = `${number(latest.matched)} matches · ${latest.elapsedMs.toFixed(2)} ms`;
  const content = $('results'); content.replaceChildren();
  if (!latest.results.length) {
    const empty = node('div','empty-state');
    empty.append(node('div','empty-symbol','⌕'),node('h3','','No matching documents'),
      node('p','','Try a broader query, use OR, or check a misspelled term with the BK-Tree word checker.'));
    content.append(empty); return;
  }
  latest.results.forEach((result,index) => {
    const item = node('button','result-card'); item.type = 'button';
    const top = node('div','result-top');
    top.append(node('span','rank-number',String(index+1).padStart(2,'0')),node('span','result-title',result.title),
      node('span','score-label',`BM25 ${result.score.toFixed(4)}`));
    const bottom = node('div','result-bottom');
    bottom.append(node('span','',`DOC ${String(result.id).padStart(2,'0')} · ${number(result.tokens)} words`),node('span','','Read document ↗'));
    item.append(top,node('p','result-snippet',result.snippet+(result.snippet.length >= 220 ? '…' : '')),bottom);
    item.addEventListener('click',() => openDocument(result.id)); content.append(item);
  });
}
function setTab(tab) {
  activeTab = tab;
  for (const button of document.querySelectorAll('[data-tab]')) {
    const selected = button.dataset.tab === tab;
    button.setAttribute('aria-selected',String(selected)); button.tabIndex = selected ? 0 : -1;
  }
  $('inspector-content').setAttribute('aria-labelledby',`tab-${tab}`); renderInspector();
}
const tabs = [...document.querySelectorAll('[data-tab]')];
tabs.forEach((button,index) => {
  button.addEventListener('click',() => setTab(button.dataset.tab));
  button.addEventListener('keydown',event => {
    if (!['ArrowRight','ArrowLeft','Home','End'].includes(event.key)) return;
    event.preventDefault();
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length-1 : (index+(event.key === 'ArrowRight' ? 1 : -1)+tabs.length)%tabs.length;
    tabs[next].focus(); setTab(tabs[next].dataset.tab);
  });
});
function renderInspector() {
  const content = $('inspector-content'); content.replaceChildren();
  if (!latest) {
    const empty = node('div','inspector-empty');
    empty.append(node('span','','{ }'),node('p','','Run a search to inspect term postings, the parsed expression tree, and heap decisions.'));
    content.append(empty); return;
  }
  if (activeTab === 'postings') {
    content.append(node('p','structure-caption','The HashMap finds each term’s linked posting list. Nodes store document IDs, word positions, and optional skip pointers.'));
    for (const posting of latest.postings) {
      const group = node('div','posting-group'), heading = node('div','posting-title',posting.term);
      heading.append(node('small','',`${posting.length} document${posting.length===1?'':'s'}`)); group.append(heading);
      const nodes = node('div','posting-nodes');
      posting.nodes.forEach((entry,index) => {
        if (index) nodes.append(node('span','posting-arrow','→'));
        const item = node('span','posting-node',`D${entry.id}`);
        item.title = `Frequency ${entry.frequency}; positions ${entry.positions.join(', ')}`;
        if (entry.skip !== null) item.append(node('span','skip-target',`skip → D${entry.skip}`));
        nodes.append(item);
      });
      if (!posting.nodes.length) nodes.append(node('span','muted','Term not in the index'));
      group.append(nodes);
      const sample = posting.nodes.slice(0,3).map(entry=>`D${entry.id} positions [${entry.positions.join(', ')}${entry.frequency>entry.positions.length?', …':''}]`).join(' · ');
      if (sample) group.append(node('p','posting-details',sample));
      if (posting.length > posting.nodes.length) group.append(node('p','posting-details',`Showing the first ${posting.nodes.length} posting nodes.`));
      content.append(group);
    }
    if (latest.postings.length === 10) content.append(node('p','structure-caption','This view shows up to ten query terms.'));
  } else if (activeTab === 'ast') {
    content.append(node('p','structure-caption','Shunting-Yard stacks build this expression tree. NOT has highest precedence, then AND, then OR. Adjacent words receive implicit AND.'));
    const tree = node('ul','ast-tree'); tree.append(astNode(latest.ast)); content.append(tree);
  } else {
    const heap = latest.heap;
    content.append(node('p','structure-caption',`A bounded min-heap retains up to ${latest.k} candidates. A higher score replaces the lowest retained score at the root.`));
    const summary = node('div','heap-summary');
    for (const [label,value] of [['Candidates',heap.offered],['Replaced',heap.replaced],['Discarded',heap.discarded]]) {
      const item = node('span','',label); item.prepend(node('strong','',number(value))); summary.append(item);
    }
    content.append(summary);
    if (!heap.retained.length) content.append(node('p','structure-caption','No matches reached the heap.'));
    else {
      content.append(node('p','heap-subheading','Final retained set, lowest score first'));
      heap.retained.forEach((item,index) => {
        const row = node('div',`heap-row${index===0?' root':''}`);
        row.append(node('span','',`D${item.id}${index===0?' · root / eviction boundary':''}`),node('span','',item.score.toFixed(4)));content.append(row);
      });
      content.append(node('p','heap-subheading','Selection trace'));
      const events = node('div','heap-events');
      heap.events.forEach(item=>events.append(node('div','',`D${item.id} ${item.score.toFixed(4)} · ${item.action}`)));
      content.append(events);
      if (heap.offered > heap.events.length) content.append(node('p','structure-caption',`Trace limited to the first ${heap.events.length} candidates.`));
    }
  }
}
function astNode(ast) {
  const item = node('li','');
  item.append(node('span',`ast-label${ast.children.length?' ast-operator':''}`,ast.label));
  if (ast.children.length) {
    const children = node('ul',''); ast.children.forEach(child=>children.append(astNode(child))); item.append(children);
  }
  return item;
}

function closeSuggestions() {
  autoSequence++; $('autocomplete').hidden = true; $('query').setAttribute('aria-expanded','false');
  $('query').removeAttribute('aria-activedescendant'); suggestionIndex=-1;
}
$('query').addEventListener('input',() => {
  clearTimeout(autoTimer); closeSuggestions();
  const match = $('query').value.match(/([\p{L}\p{N}]+)$/u);
  if (!match || /^(AND|OR|NOT)$/i.test(match[1]) || match[1].length < 2) return;
  const sequence = autoSequence;
  autoTimer = setTimeout(async () => {
    try {
      const data = await api(`/api/autocomplete?prefix=${encodeURIComponent(match[1])}`);
      if (sequence!==autoSequence) return;
      const options = data.suggestions.filter(term=>term!==match[1].toLowerCase());
      if (!options.length) return;
      $('autocomplete').replaceChildren();
      options.forEach((word,index)=>{
        const button=node('button','',word);button.type='button';button.id=`suggestion-${index}`;
        button.setAttribute('role','option'); button.setAttribute('aria-selected','false');
        button.append(node('span','','Trie prefix match'));
        button.addEventListener('click',()=>selectSuggestion(word)); $('autocomplete').append(button);
      });
      $('autocomplete').hidden=false;$('query').setAttribute('aria-expanded','true');
    } catch { closeSuggestions(); }
  },180);
});
function selectSuggestion(word) {
  $('query').value=$('query').value.replace(/[\p{L}\p{N}]+$/u,word);
  closeSuggestions(); $('query').focus(); $('search-form').requestSubmit();
}
$('query').addEventListener('keydown',event=>{
  const options=[...$('autocomplete').querySelectorAll('button')];
  if ($('autocomplete').hidden) return;
  if (event.key==='Escape') { closeSuggestions(); return; }
  if (event.key==='ArrowDown'||event.key==='ArrowUp') {
    event.preventDefault(); suggestionIndex=(suggestionIndex+(event.key==='ArrowDown'?1:-1)+options.length)%options.length;
    options.forEach((button,index)=>{button.classList.toggle('active',index===suggestionIndex);button.setAttribute('aria-selected',String(index===suggestionIndex));});
    $('query').setAttribute('aria-activedescendant',options[suggestionIndex].id);
  } else if (event.key==='Enter' && suggestionIndex>=0) {
    event.preventDefault(); selectSuggestion(options[suggestionIndex].firstChild.textContent);
  }
});
$('query').addEventListener('blur',()=>setTimeout(closeSuggestions,180));
$('syntax-toggle').addEventListener('click',()=>{
  const open=$('syntax-help').hidden; $('syntax-help').hidden=!open; $('syntax-toggle').setAttribute('aria-expanded',String(open));
});
$('spell-form').addEventListener('submit',async event=>{
  event.preventDefault(); const sequence=++spellSequence; const word=$('spell-word').value.trim();
  $('spell-results').replaceChildren(node('p','spell-caption','Checking edit distances…'));
  try {
    const data=await api(`/api/spell?term=${encodeURIComponent(word)}`);
    if(sequence!==spellSequence)return;
    const matches=node('div','spell-matches');
    data.suggestions.forEach(item=>{
      const button=node('button','',`${item.word} · ${item.distance} edit${item.distance===1?'':'s'}`);
      button.type='button'; button.addEventListener('click',()=>{$('query').value=item.word;$('search-form').requestSubmit();}); matches.append(button);
    });
    $('spell-results').replaceChildren(data.suggestions.length?matches:node('p','spell-caption','No vocabulary terms found within two edits.'));
  } catch(ex){if(sequence===spellSequence)$('spell-results').replaceChildren(node('p','spell-caption',ex.message));}
});

async function openDocument(id) {
  try {
    const doc=await api(`/api/document?id=${id}`);
    $('document-title').textContent=doc.title; $('document-meta').textContent=`DOCUMENT ${String(id).padStart(2,'0')}`;
    $('document-content').textContent=doc.content;$('document-dialog').showModal();
  }catch(ex){toast(ex.message);}
}
function showCollection(){ $('collection-feedback').textContent='';$('collection-feedback').classList.remove('error');$('collection-dialog').showModal(); }
$('manage-collection').addEventListener('click',showCollection);$('add-documents').addEventListener('click',showCollection);
document.querySelectorAll('[data-close]').forEach(button=>button.addEventListener('click',()=>$(button.dataset.close).close()));
for(const dialog of document.querySelectorAll('dialog'))dialog.addEventListener('click',event=>{if(event.target===dialog){const r=dialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)dialog.close();}});
function busyCollection(busy) {
  collectionBusy=busy;
  $('collection-dialog').querySelectorAll('button,input').forEach(input=>{if(!input.dataset.close)input.disabled=busy;});
  $('search-button').disabled=busy;$('add-documents').disabled=busy;$('manage-collection').disabled=busy;
}
async function loadCollection(values) {
  if(collectionBusy)return;
  busyCollection(true);$('collection-feedback').textContent='Reading files and building the index…';$('collection-feedback').classList.remove('error');
  try {
    const data=await api('/api/load',values);renderStatus(data);ready();$('query').value='';$('spell-results').replaceChildren(node('p','spell-caption','Find vocabulary terms within two edits.'));
    $('collection-dialog').close();toast(`Opened ${data.collection}: ${number(data.documents)} documents.`);
  }catch(ex){$('collection-feedback').textContent=ex.message;$('collection-feedback').classList.add('error');}
  finally{busyCollection(false);}
}
document.querySelectorAll('[data-dataset]').forEach(button=>button.addEventListener('click',()=>loadCollection({dataset:button.dataset.dataset})));
$('folder-form').addEventListener('submit',event=>{event.preventDefault();loadCollection({directory:$('folder-path').value});});
$('choose-files').addEventListener('click',()=>$('files-input').click());$('choose-folder').addEventListener('click',()=>$('folder-input').click());
async function importFiles(input) {
  const selected=[...input.files];input.value='';
  if(!selected.length)return;
  const files=selected.filter(file=>/\.(txt|md)$/i.test(file.name));
  if(!files.length){$('collection-feedback').textContent='Choose .txt or .md files.';return;}
  if(files.length>100||files.some(file=>file.size>1_000_000)){$('collection-feedback').textContent='Select up to 100 documents, each under 1 MB.';$('collection-feedback').classList.add('error');return;}
  busyCollection(true);$('collection-feedback').classList.remove('error');let imported=0;const failures=[];
  for(const file of files){
    $('collection-feedback').textContent=`Indexing ${imported+failures.length+1} of ${files.length}: ${file.name}`;
    try{await api('/api/document',{title:file.name,content:await file.text()});imported++;}catch(ex){failures.push(`${file.name}: ${ex.message}`);}
  }
  try{renderStatus(await api('/api/status'));if(imported)ready();}catch(ex){failures.push(ex.message);}
  busyCollection(false);
  if(failures.length){$('collection-feedback').textContent=`Added ${imported} documents. ${failures.join(' ' )}`;$('collection-feedback').classList.add('error');}
  else{$('collection-dialog').close();toast(`Added ${imported} document${imported===1?'':'s'} to the index.`);}
}
$('files-input').addEventListener('change',()=>importFiles($('files-input')));$('folder-input').addEventListener('change',()=>importFiles($('folder-input')));
try{renderStatus(await api('/api/status'));}catch(ex){error('Could not reach the local Java server. Start the project using start-web.ps1, then reload this page.');$('connection-label').textContent='Local server unavailable';$('search-button').disabled=true;}
