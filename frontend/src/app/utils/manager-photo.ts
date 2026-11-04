export async function prepareManagerPhoto(file:File):Promise<string>{
  if(!['image/png','image/jpeg','image/webp'].includes(file.type)||file.size>5*1024*1024)throw Error('Escolha uma imagem PNG, JPEG ou WebP de até 5 MB.');
  let bitmap:ImageBitmap;
  try{bitmap=await createImageBitmap(file);}catch{throw Error('Não foi possível abrir essa imagem. Escolha outra foto.');}
  try{
    const canvas=document.createElement('canvas');canvas.width=256;canvas.height=256;
    const context=canvas.getContext('2d');if(!context)throw Error('Não foi possível preparar a foto.');
    const side=Math.min(bitmap.width,bitmap.height);
    context.drawImage(bitmap,(bitmap.width-side)/2,(bitmap.height-side)/2,side,side,0,0,256,256);
    let data=canvas.toDataURL('image/png');
    if(data.length>349500){const smaller=document.createElement('canvas');smaller.width=192;smaller.height=192;smaller.getContext('2d')!.drawImage(canvas,0,0,192,192);data=smaller.toDataURL('image/png');}
    return data;
  }finally{bitmap.close();}
}
