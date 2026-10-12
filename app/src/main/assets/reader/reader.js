/* Only handle app-generated DOM: highlighting, image retries, and fullscreen controls that preserve other interactions. */
document.querySelectorAll('pre code').forEach(function (code) {
  if (window.hljs) {
    var language = (code.className.match(/language-([^\s]+)/) || [])[1];
    if (language && hljs.getLanguage(language)) hljs.highlightElement(code);
  }
});
document.querySelectorAll('table').forEach(function (table) {
  var wrapper = document.createElement('div'); wrapper.className = 'table-scroll';
  table.parentNode.insertBefore(wrapper, table); wrapper.appendChild(table);
});
document.querySelectorAll('img').forEach(function (img) {
  img.classList.add('image-loading');
  var errorBox;
  function loaded() { img.classList.remove('image-loading'); if(errorBox) errorBox.remove(); img.hidden = false; }
  function failed() {
    if (errorBox && errorBox.isConnected) return;
    img.hidden = true; errorBox = document.createElement('div'); errorBox.className = 'image-error';
    var label = document.createElement('div'); label.textContent = 'Image failed to load · ' + (img.alt || 'No alternative text');
    errorBox.appendChild(label);
    var retry = document.createElement('button'); retry.textContent = 'Reload';
    retry.onclick = function () { errorBox.remove(); img.hidden = false; img.src = img.src.split('&retry=')[0] + '&retry=' + Date.now(); };
    errorBox.appendChild(retry);
    var url = new URL(img.src), destination = url.searchParams.get('destination') || '';
    if (!/^[a-z][a-z0-9+.-]*:/i.test(destination)) {
      var permission = document.createElement('button'); permission.textContent = 'Grant image folder access';
      permission.onclick = function () { location.href = 'mustang://authorize-images'; };
      errorBox.appendChild(permission);
    }
    img.after(errorBox);
  }
  img.addEventListener('load', loaded); img.addEventListener('error', failed);
  if (img.complete) { if (img.naturalWidth) loaded(); else failed(); }
});
var start;
document.addEventListener('pointerdown', function(e) { start = { x:e.clientX, y:e.clientY, time:Date.now() }; });
document.addEventListener('pointerup', function(e) {
  if (!start || Date.now()-start.time > 300 || Math.abs(e.clientX-start.x) > 8 || Math.abs(e.clientY-start.y) > 8) return;
  if (e.target.closest('a,button,input,pre,.table-scroll') || window.getSelection().toString()) return;
  location.href = 'mustang://toggle';
});
