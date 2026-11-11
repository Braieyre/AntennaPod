const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const htmlPath=path.resolve(__dirname,'../visual-review.html');
const html=fs.readFileSync(htmlPath,'utf8');
const tokens=JSON.parse(html.match(/<script id="design-tokens" type="application\/json">([\s\S]*?)<\/script>/)[1]);
const script=[...html.matchAll(/<script(?: [^>]*)?>([\s\S]*?)<\/script>/g)].at(-1)[1];
const handlers={},nodes=new Map(),storage=new Map(),animations=[];
function node(key){if(!nodes.has(key)){const children=new Map(),classes=new Set();nodes.set(key,{innerHTML:'',textContent:key==='design-tokens'?JSON.stringify(tokens):'',style:{},dataset:{},classList:{toggle(k,force){const enabled=force===undefined?!classes.has(k):force;enabled?classes.add(k):classes.delete(k);return enabled},contains:k=>classes.has(k),add:k=>classes.add(k),remove:k=>classes.delete(k)},querySelector(s){if(!children.has(s))children.set(s,node(key+' '+s));return children.get(s)},querySelectorAll:()=>[],scrollIntoView(){},animate(frames,options){animations.push(options);return{finished:Promise.resolve(),cancel(){}}}})}return nodes.get(key)}
const context=vm.createContext({document:{getElementById:node,querySelectorAll:()=>[],addEventListener:(event,callback)=>handlers[event]=callback},window:{matchMedia:()=>({matches:false})},localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)},setTimeout:()=>1,clearTimeout(){},console});
vm.runInContext(script,context);
const value=expression=>vm.runInContext(expression,context);
function click(data,attrs=[]){const b=node('button'+JSON.stringify(data)+attrs.join());b.dataset=data;b.hasAttribute=key=>attrs.includes(key);handlers.click({target:{closest:()=>b}});return b}
(async()=>{
assert.equal(value('DIRS.length'),3);assert(node('screen-boards').innerHTML.includes('current-home.png'));assert.equal((node('screen-boards').innerHTML.match(/class="device"/g)||[]).length,9);
click({focus:'B'});assert.equal(value('focus'),'B');click({mode:'dark'});assert.equal(value('mode'),'dark');
click({choose:'A'});assert.equal(JSON.parse(storage.get('mypodcasts-visual-choice')).direction,'A');click({iconChoice:'C'});assert.equal(value('chosenIcon'),'C');
click({motion:'slow'});await value('playExpand()');assert(animations.some(a=>a.duration===1040));
click({motion:'reduced'});const before=animations.length;await value('playComplete()');assert.equal(animations.length,before);assert(node('completion-state').textContent.includes('无位移'));
click({motion:'normal'});await value('playComplete()');assert(animations.some(a=>a.duration===200));assert(animations.some(a=>a.duration===220));assert(node('completion-state').textContent.includes('420 ms'));
console.log(JSON.stringify({result:'PASS',scope:'Node VM interaction logic only; no browser pixel rendering',screens:9,checks:['focus','light-dark-state','local-choice','icon-choice','4x-motion-duration','reduced-direct-terminal','completion-200-plus-220']},null,2));
})().catch(error=>{console.error(error);process.exitCode=1});
