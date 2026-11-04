import { inject, Injectable } from '@angular/core';
import { tap } from 'rxjs';
import { SaveService } from './save.service';
import { navigate } from '../utils/navigation';

@Injectable({ providedIn: 'root' })
export class AccessFlowService {
  private readonly saves = inject(SaveService);
  afterLogin() {
    return this.saves.available().pipe(tap(saves => {
      const recent=saves[0];
      if (recent) { this.saves.remember(recent); navigate('leagues'); }
      else navigate('new-league');
    }));
  }
}
