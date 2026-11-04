import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app/app';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';

registerLocaleData(localePt);
bootstrapApplication(App, { providers: [provideHttpClient(), { provide: LOCALE_ID, useValue: 'pt-BR' }] }).catch(console.error);
