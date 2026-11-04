import { CurrencyPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { TeamManagement } from '../../services/management.service';
import { ManagementPage } from '../../utils/management-navigation';

@Component({selector:'app-management-section',imports:[CurrencyPipe],templateUrl:'./management-section.html',styleUrl:'./management-section.css'})
export class ManagementSectionPage {
  readonly section=input.required<ManagementPage>();
  readonly club=input.required<TeamManagement>();
  readonly coach=input('Treinador');
  readonly dark=input(true);
  readonly navigate=output<ManagementPage>();
  readonly toggleTheme=output<void>();
}
