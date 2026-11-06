import { bootstrapApplication } from '@angular/platform-browser';
import { apiTimeoutInterceptor } from './app/services/api-timeout.interceptor';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { App } from './app/app';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';

registerLocaleData(localePt);
bootstrapApplication(App, { providers: [provideHttpClient(withInterceptors([apiTimeoutInterceptor])), { provide: LOCALE_ID, useValue: 'pt-BR' }] }).catch(console.error);
