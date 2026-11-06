import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError, timeout, TimeoutError } from 'rxjs';

export const apiTimeoutInterceptor: HttpInterceptorFn = (request, next) => {
 if (!request.url.startsWith('/api/')) return next(request);
 return next(request).pipe(timeout({each:55000}),catchError(error=>{
  if(error instanceof TimeoutError)return throwError(()=>new HttpErrorResponse({status:504,url:request.url,error:{message:'O servidor demorou para responder. Atualize a tela antes de tentar novamente.'}}));
  if(error instanceof HttpErrorResponse&&[502,503,504].includes(error.status)&&!error.error?.message)return throwError(()=>new HttpErrorResponse({status:error.status,url:request.url,error:{message:'O servidor está ocupado ou demorou para responder. Atualize a tela antes de tentar novamente.'}}));
  return throwError(()=>error);
 }));
};
