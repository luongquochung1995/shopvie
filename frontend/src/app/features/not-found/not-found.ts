import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { Seo } from '../../core/seo/seo.service';
import { EmptyState } from '../../shared/ui/empty-state';

@Component({
  selector: 'app-not-found',
  imports: [EmptyState, RouterLink, TranslocoDirective],
  template: `
    <app-empty-state *transloco="let t" [title]="t('notFound.title')" [description]="t('notFound.description')" icon="pi-compass">
      <a routerLink="/" class="text-brand-500">{{ t('common.backHome') }}</a>
    </app-empty-state>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class NotFound {
  constructor() {
    const t = inject(TranslocoService);
    inject(Seo).notFound(t.translate('title.notFound'), t.translate('notFound.description'));
  }
}
