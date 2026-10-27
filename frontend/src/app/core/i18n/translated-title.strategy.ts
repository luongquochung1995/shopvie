import { DestroyRef, inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

/**
 * `title` của route là key dịch (vd. 'title.home'); dịch lại khi đổi ngôn ngữ.
 * Route không có title (trang sản phẩm/danh mục) do component tự đặt tiêu đề.
 */
@Injectable()
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);
  private currentKey: string | undefined;

  constructor() {
    super();
    this.transloco.langChanges$.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.apply());
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.currentKey = this.buildTitle(snapshot);
    this.apply();
  }

  private apply(): void {
    if (this.currentKey) {
      this.title.setTitle(this.transloco.translate(this.currentKey));
    }
  }
}
