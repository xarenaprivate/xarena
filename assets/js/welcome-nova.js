/* Welcome splash (mockup 75b137cb). Shows once per device, after the loading splash. */
(function(){
'use strict';
function svg(s,p,c){return '<svg width="'+s+'" height="'+s+'" viewBox="0 0 24 24" fill="none" stroke="'+(c||'#ffd783')+'" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'+p+'</svg>';}
var LOGO='<svg width="34" height="34" viewBox="0 0 40 40"><path d="M20 2 36 10v14c0 8-7 12.7-16 14C11 36.7 4 32 4 24V10Z" fill="#0b0b14" stroke="#f5b301" stroke-width="2.5"/><path d="M13 13l14 14M27 13L13 27" stroke="#ffd783" stroke-width="3.2"/></svg>';
var SHIELD='<svg width="72" height="72" viewBox="0 0 40 40"><path d="M20 2 36 10v14c0 8-7 12.7-16 14C11 36.7 4 32 4 24V10Z" fill="rgba(11,11,20,.85)" stroke="#f5b301" stroke-width="2"/><path d="M13 13l14 14M27 13L13 27" stroke="#ffd783" stroke-width="3"/></svg>';
var PAD=svg(46,'<rect x="2.5" y="7.5" width="19" height="10.5" rx="5"/><path d="M7 11v3.4M5.3 12.7h3.4"/><circle cx="15.4" cy="11.6" r=".9" fill="#ffd783"/><circle cx="18" cy="13.6" r=".9" fill="#ffd783"/>','#c9b8e6');
var TROPHY=svg(40,'<path d="M8 4h8v4a4 4 0 0 1-8 0Z"/><path d="M8 5H5a3 3 0 0 0 3 4M16 5h3a3 3 0 0 1-3 4"/><path d="M12 12v3M8 20h8M10 15h4l1 5h-6Z"/>');
var SPARK=svg(30,'<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M18 6l-2.5 2.5M8.5 15.5 6 18"/>');
var SPARK2=svg(24,'<path d="M12 4l1.7 4.6L18 10l-4.3 1.4L12 16l-1.7-4.6L6 10l4.3-1.4Z"/>');
function show(){
 var el=document.createElement('div');el.className='xwl';el.id='xwl';
 el.innerHTML='<div class="xwl-hex"></div>'+
  '<div class="xwl-logo"><span class="mark">'+LOGO+'<span style="text-align:left"><b>XARENA</b><span>ESPORTS</span></span></span></div>'+
  '<div class="xwl-art"><div class="ring">'+SHIELD+'</div>'+
   '<span class="fl f1">'+PAD+'</span><span class="fl f2">'+TROPHY+'</span><span class="fl f3">'+SPARK+'</span><span class="fl f4">'+SPARK2+'</span></div>'+
  '<div class="xwl-copy"><h1>Welcome to<b>XARENA</b></h1><p>Free Fire Esports Made in India</p>'+
  '<a class="xwl-go" href="account.html">GET STARTED</a>'+
  '<div class="xwl-dots"><i class="on"></i><i></i><i></i></div></div>';
 document.body.appendChild(el);
 function done(){try{localStorage.setItem('xr_welcomed_v1','1');}catch(e){} el.style.opacity='0';el.style.transition='opacity .35s ease';setTimeout(function(){el.remove();},380);}
 el.querySelector('.xwl-go').addEventListener('click',function(){try{localStorage.setItem('xr_welcomed_v1','1');}catch(e){}});
 el.addEventListener('click',function(e){if(e.target===el)done();});
}
function boot(){
 try{if(localStorage.getItem('xr_welcomed_v1'))return;}catch(e){return;}
 function afterSplash(){var s=document.getElementById('xr-splash');if(s){setTimeout(afterSplash,300);return;}show();}
 afterSplash();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
