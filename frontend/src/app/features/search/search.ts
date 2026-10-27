import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { EmptyState } from '../../shared/ui/empty-state';

/** Tìm kiếm full-text làm ở Phase 5 (P5-3, P5-7). */
@Component({
  selector: 'app-search',
  imports: [EmptyState, RouterLink, TranslocoDirective],
  template: `
    <app-empty-state *transloco="let t" icon="pi-search" [title]="t('search.title', { q: q() ?? '' })"
                     [description]="t('search.comingSoon')">
      <a routerLink="/" class="text-brand-500">{{ t('common.backHome') }}</a>
    </app-empty-state>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Search {
  readonly q = input<string>();
}
