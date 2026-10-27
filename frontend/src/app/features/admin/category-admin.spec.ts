import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminCategory, ApiResponse, AuthResponse, CategoryNode } from '../../core/api/api.models';
import { ApiError } from '../../core/api/api-error';
import { errorInterceptor } from '../../core/api/error.interceptor';
import { authInterceptor } from '../../core/auth/auth.interceptor';
import { AuthStore } from '../../core/auth/auth.store';
import { mockBackendInterceptor, mockDb, resetMockDb } from '../../mock/mock-backend.interceptor';
import { MOCK_PASSWORD } from '../../mock/mock-data';
import { flattenTree } from './category-admin';

const node = (id: number, position: number, children: CategoryNode[] = []): CategoryNode => ({
  id, parentId: null, name: `C${id}`, slug: `c${id}`, level: 0, position, iconUrl: null, children,
});

describe('flattenTree', () => {
  it('lists parents before children, ordered by position', () => {
    const rows = flattenTree([node(1, 1, [node(3, 1), node(4, 0)]), node(2, 0)]);
    expect(rows.map((r) => [r.node.id, r.depth])).toEqual([[2, 0], [1, 0], [4, 1], [3, 1]]);
  });
});

describe('mock backend — admin categories', () => {
  const API = environment.apiBaseUrl;
  let http: HttpClient;
  const post = <T>(url: string, body: unknown) => firstValueFrom(http.post<ApiResponse<T>>(API + url, body)).then((r) => r?.data);
  const error = (p: Promise<unknown>) => p.then(() => null, (e: ApiError) => e);

  beforeEach(async () => {
    resetMockDb();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor, authInterceptor, mockBackendInterceptor(API, 0)]))],
    });
    http = TestBed.inject(HttpClient);
    TestBed.inject(AuthStore).setSession(await post<AuthResponse>('/auth/login', { email: 'admin@shopvi.local', password: MOCK_PASSWORD }));
  });

  it('creates up to 3 levels with a generated slug, then rejects a 4th level', async () => {
    const root = await post<AdminCategory>('/admin/categories', { parentId: null, name: 'Thể Thao', slug: null, position: 9, iconUrl: null });
    expect(root).toMatchObject({ slug: 'the-thao', level: 0 });
    const child = await post<AdminCategory>('/admin/categories', { parentId: root.id, name: 'Bóng đá', slug: null, position: 0, iconUrl: null });
    const leaf = await post<AdminCategory>('/admin/categories', { parentId: child.id, name: 'Giày đá bóng', slug: null, position: 0, iconUrl: null });
    expect(leaf.level).toBe(2);
    const tooDeep = await error(post('/admin/categories', { parentId: leaf.id, name: 'X', slug: null, position: 0, iconUrl: null }));
    expect(tooDeep?.code).toBe('CATEGORY_TOO_DEEP');
    const duplicate = await error(post('/admin/categories', { parentId: null, name: 'Khác', slug: 'the-thao', position: 0, iconUrl: null }));
    expect(duplicate?.code).toBe('CATEGORY_SLUG_EXISTS');
  });

  it('deletes only empty categories', async () => {
    const withProducts = mockDb().catalog.products[0].categoryId;
    const notEmpty = await error(firstValueFrom(http.delete(`${API}/admin/categories/${withProducts}`)));
    expect(notEmpty?.code).toBe('CATEGORY_NOT_EMPTY');

    const created = await post<AdminCategory>('/admin/categories', { parentId: null, name: 'Tạm', slug: null, position: 0, iconUrl: null });
    await firstValueFrom(http.delete(`${API}/admin/categories/${created.id}`));
    expect(mockDb().catalog.categories.some((c) => c.id === created.id)).toBe(false);
  });
});
