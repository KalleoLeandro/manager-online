function rgb(hex: string): number[] { return [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)); }
function mix(hex: string,target: string,factor: number): string {
  const a=rgb(hex), b=rgb(target);
  return '#'+a.map((n,i)=>Math.round(n*(1-factor)+b[i]*factor).toString(16).padStart(2,'0')).join('');
}
function luminance(hex: string): number {
  const c=rgb(hex).map(v=>v/255).map(v=>v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4);
  return c[0]*0.2126+c[1]*0.7152+c[2]*0.0722;
}
export function contrast(a: string,b: string): number {
  const [hi,lo]=[luminance(a),luminance(b)].sort((a,b)=>b-a); return (hi+0.05)/(lo+0.05);
}
export function clubTheme(primary: string,secondary: string,dark: boolean,awayAccent?: string,homeAccent?: string) {
  const safe=(value:string)=>/^#[0-9a-f]{6}$/i.test(value)?value.toLowerCase():'#334155';
  const home=safe(primary), away=safe(secondary);
  const detail=safe(awayAccent||secondary);
  const homeDetail=safe(homeAccent||'#ffffff');
  const tint=luminance(home)>.85?homeDetail:home;
  const background=dark?mix(away,'#080a0e',0.88):mix(tint,'#ffffff',.96);
  const surface=dark?mix(away,'#101318',0.84):'#ffffff';
  const border=dark?mix(detail,surface,0.72):mix(tint,'#ffffff',.82);
  const base=dark?(contrast(detail,surface)>=3?detail:away):home;
  let accent=base;
  for(let step=1;(contrast(accent,background)<4.5||contrast(accent,surface)<4.5)&&step<=20;step++) accent=mix(base,dark?'#ffffff':'#000000',step/20);
  const ink=(color:string)=>contrast(color,'#ffffff')>contrast(color,'#000000')?'#ffffff':'#000000';
  const jersey=dark?away:home;
  return { background,surface,border,rowHighlight:dark?mix(away,surface,0.92):mix(tint,'#ffffff',.97),accent,accentText:ink(accent),primary:home,secondary:away,heroInk:ink(home),jersey,jerseyInk:ink(jersey),jerseyEdge:dark?detail:homeDetail,hero:dark?away:home,heroText:ink(dark?away:home) };
}
