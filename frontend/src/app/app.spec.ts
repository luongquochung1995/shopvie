import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { translocoTesting } from '../testing/transloco-testing';
import { RealtimeService } from './core/realtime/realtime.service';
import { MockRealtimeService } from './mock/mock-realtime';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App, translocoTesting()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService,
        { provide: RealtimeService, useClass: MockRealtimeService },
      ],
    }).compileComponents();
  });

  it('renders header, main and footer', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('app-header')).toBeTruthy();
    expect(el.querySelector('main router-outlet')).toBeTruthy();
    expect(el.querySelector('app-footer')).toBeTruthy();
  });
});
