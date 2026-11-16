const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function classes(initial=[]) { const values=new Set(initial); return {add:v=>values.add(v),contains:v=>values.has(v),remove:v=>values.delete(v)}; }
const observers=[];
const buttons=['ROUND_ROBIN','GROUP_STAGE','SINGLE_ELIMINATION'].map((id,i)=>({id,disabled:false,textContent:id,classList:classes(i===0?['active']:[]),querySelector(){return null;},click(){buttons.forEach(b=>b.classList.remove('active'));this.classList.add('active');}}));
const grid={classList:classes(),querySelectorAll(){return buttons;},after(select){this.select=select;}};
let initialize;
vm.runInNewContext(fs.readFileSync('web/js/standalone-format-layout.js','utf8'),{
 window:{addEventListener(type,fn){initialize=fn;}},
 document:{querySelectorAll(){return [grid];},getElementById(){return null;},createElement(){return {children:[],listeners:{},setAttribute(){},appendChild(el){this.children.push(el);},get options(){return this.children;},addEventListener(type,fn){this.listeners[type]=fn;}};}},
 MutationObserver:class{constructor(fn){observers.push(fn);}observe(){}},Array
});
initialize();
assert.equal(grid.select.value,'ROUND_ROBIN');
grid.select.value='GROUP_STAGE';grid.select.listeners.change();
assert.ok(buttons[1].classList.contains('active'));
buttons[2].disabled=true;observers[0]();assert.equal(grid.select.options[2].disabled,true);
buttons.forEach(b=>b.disabled=true);observers[0]();assert.equal(grid.select.disabled,true);
buttons.forEach(b=>b.disabled=false);buttons[0].classList.remove('active');buttons[1].classList.remove('active');buttons[2].classList.add('active');observers[0]();
assert.equal(grid.select.value,'SINGLE_ELIMINATION');assert.equal(grid.select.disabled,false);
console.log('PASS: compact selector delegates to original buttons, restores active format, respects stage locks');
