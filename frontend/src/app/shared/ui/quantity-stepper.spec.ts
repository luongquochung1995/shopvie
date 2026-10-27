import { TestBed } from '@angular/core/testing';
import { translocoTesting } from '../../../testing/transloco-testing';
import { QuantityStepper } from './quantity-stepper';

describe('QuantityStepper', () => {
  async function setup(value: number, max: number) {
    await TestBed.configureTestingModule({ imports: [QuantityStepper, translocoTesting()] }).compileComponents();
    const fixture = TestBed.createComponent(QuantityStepper);
    fixture.componentRef.setInput('value', value);
    fixture.componentRef.setInput('max', max);
    const emitted: number[] = [];
    fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
    await fixture.whenStable();
    const [minus, plus] = fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    return { fixture, emitted, minus, plus, input };
  }

  it('steps within [1, max] and disables buttons at the bounds', async () => {
    const { emitted, minus, plus } = await setup(1, 3);
    expect(minus.disabled).toBe(true);
    plus.click();
    expect(emitted).toEqual([2]);
  });

  it('clamps typed values to max', async () => {
    const { emitted, input } = await setup(2, 5);
    input.value = '99';
    input.dispatchEvent(new Event('change'));
    expect(emitted).toEqual([5]);
    expect(input.value).toBe('5');
  });

  it('disables plus at max', async () => {
    const { plus } = await setup(5, 5);
    expect(plus.disabled).toBe(true);
  });
});
