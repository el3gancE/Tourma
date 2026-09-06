const fs = require('fs');
const path = require('path');

function walk(dir, filelist = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filepath = path.join(dir, file);
    if (fs.statSync(filepath).isDirectory()) {
      walk(filepath, filelist);
    } else if (file.endsWith('.js') || file.endsWith('.jsp')) {
      filelist.push(filepath);
    }
  }
  return filelist;
}

const files = walk('c:/Users/admin/Documents/NetBeansProjects/Tourma/web');
const getMap = {};
const regexGet = /localStorage\.getItem\s*\(\s*([^)\n]+)\)/g;

for (const file of files) {
  const content = fs.readFileSync(file, 'utf8');
  let match;
  const relPath = path.relative('c:/Users/admin/Documents/NetBeansProjects/Tourma/web', file);
  
  while ((match = regexGet.exec(content)) !== null) {
    const k = match[1].trim();
    if (!getMap[k]) getMap[k] = new Set();
    getMap[k].add(relPath);
  }
}

console.log('=== ALL localStorage.getItem KEYS ===\n');
for (const [k, v] of Object.entries(getMap)) {
  console.log(`Key: ${k} => [${[...v].join(', ')}]`);
}
