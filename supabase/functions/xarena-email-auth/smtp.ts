// Small bounded TLS SMTP client for transactional ASCII codes only.
// No debug logging and no recipient/header/body supplied by callers.
export async function smtpSession(password:string, mail?:{email:string,code:string}) {
 const conn=await Deno.connectTls({hostname:'smtp.gmail.com',port:465});
 const timer=setTimeout(()=>{try{conn.close();}catch{}},12000);
 const reader=conn.readable.getReader(),writer=conn.writable.getWriter();
 const enc=new TextEncoder(),dec=new TextDecoder();let buffered='';
 async function line(){
  while(!buffered.includes('\r\n')){const r=await reader.read();if(r.done)throw Error('SMTP closed');buffered+=dec.decode(r.value,{stream:true});if(buffered.length>32768)throw Error('SMTP oversized reply');}
  const i=buffered.indexOf('\r\n'),s=buffered.slice(0,i);buffered=buffered.slice(i+2);return s;
 }
 async function reply(expected:number){let s;do{s=await line();if(!/^\d{3}[ -]/.test(s))throw Error('SMTP invalid reply');if(Number(s.slice(0,3))!==expected)throw Error('SMTP request failed');}while(s[3]==='-');}
 async function command(s:string,expected:number){await writer.write(enc.encode(s+'\r\n'));await reply(expected);}
 try{
  await reply(220);await command('EHLO xarena',250);
  await command('AUTH PLAIN '+btoa('\0xarenaprivate@gmail.com\0'+password),235);
  if(mail){
   if(!/^[A-Za-z0-9.!#$%&'*+\/=?^_`{|}~-]+@[A-Za-z0-9.-]+$/.test(mail.email)||!/^\d{6}$/.test(mail.code))throw Error('Invalid mail parameters');
   await command('MAIL FROM:<xarenaprivate@gmail.com>',250);
   await command('RCPT TO:<'+mail.email+'>',250);
   await command('DATA',354);
   const body=['From: XARENA <xarenaprivate@gmail.com>','To: <'+mail.email+'>','Subject: Your XARENA verification code','Date: '+new Date().toUTCString(),'Message-ID: <'+crypto.randomUUID()+'@gmail.com>','MIME-Version: 1.0','Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: 7bit','','Your XARENA verification code is '+mail.code+'.','','This code expires in 10 minutes. Do not share it.','If you did not request this code, ignore this email.'];
   await writer.write(enc.encode(body.join('\r\n')+'\r\n.\r\n'));await reply(250);
  }
  // Delivery is committed at DATA250. QUIT failure must not invite a duplicate send.
  try{await command('QUIT',221);}catch{if(!mail)throw Error('SMTP close failed');}
 }finally{clearTimeout(timer);try{reader.releaseLock();writer.releaseLock();conn.close();}catch{}}
}
