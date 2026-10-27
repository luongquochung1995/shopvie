import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { translocoTesting } from '../../../testing/transloco-testing';
import { OrderDetail, Review, ReviewInput } from '../../core/api/api.models';
import { ReviewApi } from '../../core/api/review.api';
import { UserApi } from '../../core/api/user.api';
import { OrderReviews } from './order-reviews';

describe('OrderReviews', () => {
  const order = { code: 'ORD1', items: [{ id: 7, productName: 'Áo thun', variantName: null }] } as unknown as OrderDetail;
  let created: ReviewInput | null;
  let uploads: string[];

  beforeEach(async () => {
    created = null;
    uploads = [];
    await TestBed.configureTestingModule({
      imports: [OrderReviews, translocoTesting()],
      providers: [
        MessageService,
        { provide: ReviewApi, useValue: {
          orderReviews: () => of([]),
          create: (_code: string, itemId: number, body: ReviewInput) => {
            created = body;
            return of({ id: 1, orderItemId: itemId, rating: body.rating, content: body.content, media: body.media } as Review);
          },
        } },
        { provide: UserApi, useValue: { uploadImage: (file: File) => {
          uploads.push(file.name);
          return of({ url: `https://cdn.test/${file.name}` });
        } } },
      ],
    }).compileComponents();
  });

  const image = (name: string, bytes = 10) => new File([new Uint8Array(bytes)], name, { type: 'image/png' });

  function pick(el: HTMLElement, files: File[]): void {
    const input = el.querySelector<HTMLInputElement>('[data-review-media] input[type="file"]')!;
    const list = Object.assign([...files], { item: (i: number) => files[i] });
    Object.defineProperty(input, 'files', { value: list, configurable: true });
    input.dispatchEvent(new Event('change'));
  }

  it('uploads up to 5 photos, skips files over 5MB and sends their URLs with the review', async () => {
    const fixture = TestBed.createComponent(OrderReviews);
    fixture.componentRef.setInput('order', order);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLButtonElement>('[data-review-item="7"] button')!.click();
    await fixture.whenStable();

    pick(el, [image('big.png', 5 * 1024 * 1024 + 1), image('a.png'), image('b.png')]);
    await fixture.whenStable();
    expect(uploads).toEqual(['a.png', 'b.png']);

    pick(el, ['c', 'd', 'e', 'f'].map((n) => image(`${n}.png`)));
    await fixture.whenStable();
    expect(uploads).toHaveLength(5);
    expect(el.querySelector('[data-review-media] input[type="file"]')).toBeNull();

    el.querySelector<HTMLButtonElement>('[data-review-media] button[aria-label]')!.click();
    await fixture.whenStable();
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(created?.media).toEqual(['b', 'c', 'd', 'e'].map((n) => `https://cdn.test/${n}.png`));
    expect(el.querySelectorAll('[data-review-item="7"] img')).toHaveLength(4);
  });
});
