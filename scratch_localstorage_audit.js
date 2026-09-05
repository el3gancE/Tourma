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
const items = [];

const regexSet = /localStorage\.setItem\s*\(\s*([^,\n]+)\s*,\s*([^)]+)\)/g;
const regexGet = /localStorage\.getItem\s*\(\s*([^)\n]+)\)/g;

for (const file of files) {
  const content = fs.readFileSync(file, 'utf8');
  let match;
  const relPath = path.relative('c:/Users/admin/Documents/NetBeansProjects/Tourma/web', file);
  
  while ((match = regexSet.exec(content)) !== null) {
    items.push({
      action: 'setItem',
      file: relPath,
      keyExpr: match[1].trim(),
      valExpr: match[2].trim().substring(0, 80)
    });
  }
}

// Group by keyExpr
const grouped = {};
for (const item of items) {
  if (!grouped[item.keyExpr]) {
    grouped[item.keyExpr] = { files: new Set(), samples: [] };
  }
  grouped[item.keyExpr].files.add(item.file);
  if (grouped[item.keyExpr].samples.length < 3) {
    grouped[item.keyExpr].samples.push(item.valExpr);
  }
}

console.log('=== ALL localStorage.setItem CALLS IN WEB ===\n');
for (const [k, v] of Object.entries(grouped)) {
  console.log(`Key: ${k}`);
  console.log(`  Files (${v.files.size}): ${[...v.files].join(', ')}`);
  console.log(`  Value samples: ${v.samples.join(' | ')}\n`);
}
