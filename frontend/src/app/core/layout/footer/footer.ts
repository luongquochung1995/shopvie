import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { LanguageSwitcher } from '../language-switcher';

@Component({
  selector: 'app-footer',
  imports: [TranslocoDirective, LanguageSwitcher],
  templateUrl: './footer.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Footer {
  protected readonly year = new Date().getFullYear();
}
