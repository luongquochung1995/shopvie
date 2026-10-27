import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  template: `
    <div class="flex flex-col items-center gap-3 px-4 py-12 text-center text-gray-500">
      <i class="pi text-5xl text-gray-300" [class]="icon()" aria-hidden="true"></i>
      <p class="font-medium text-gray-700">{{ title() }}</p>
      @if (description()) {
        <p class="text-sm">{{ description() }}</p>
      }
      <ng-content />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly icon = input('pi-inbox');
}
