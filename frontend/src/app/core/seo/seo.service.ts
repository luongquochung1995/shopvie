import { DOCUMENT } from '@angular/common';
import { inject, Injectable, REQUEST, RESPONSE_INIT } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

export interface SeoData {
  title: string;
  description: string;
  /** Đường dẫn trong site (vd. /p/ao-thun-1) — dùng cho canonical và og:url. */
  path?: string;
  image?: string | null;
  /** og:type — website | product. */
  type?: 'website' | 'product';
  /** Dữ liệu có cấu trúc schema.org (JSON-LD). */
  jsonLd?: object | null;
  /** Trang không nên lập chỉ mục (tìm kiếm, trang lỗi). */
  noindex?: boolean;
}

const JSON_LD_ID = 'seo-jsonld';
const SITE_NAME = 'ShopVie';

/**
 * Thẻ SEO cho trang (chạy cả SSR): title, meta description, canonical, Open Graph, Twitter card, robots, JSON-LD.
 * Dùng origin của request hiện tại (SSR) / trình duyệt để tạo URL tuyệt đối.
 */
@Injectable({ providedIn: 'root' })
export class Seo {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly request = inject(REQUEST, { optional: true });
  private readonly responseInit = inject(RESPONSE_INIT, { optional: true });

  set(data: SeoData): void {
    const description = clean(data.description).slice(0, 160);
    const url = data.path ? this.absolute(data.path) : null;
    this.title.setTitle(data.title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ name: 'robots', content: data.noindex ? 'noindex, follow' : 'index, follow' });
    this.meta.updateTag({ property: 'og:site_name', content: SITE_NAME });
    this.meta.updateTag({ property: 'og:title', content: data.title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:type', content: data.type ?? 'website' });
    this.meta.updateTag({ name: 'twitter:card', content: data.image ? 'summary_large_image' : 'summary' });
    this.meta.updateTag({ name: 'twitter:title', content: data.title });
    this.meta.updateTag({ name: 'twitter:description', content: description });
    if (data.image) {
      this.meta.updateTag({ property: 'og:image', content: data.image });
      this.meta.updateTag({ name: 'twitter:image', content: data.image });
    } else {
      this.meta.removeTag("property='og:image'");
      this.meta.removeTag("name='twitter:image'");
    }
    if (url) {
      this.meta.updateTag({ property: 'og:url', content: url });
    } else {
      this.meta.removeTag("property='og:url'");
    }
    this.setCanonical(url);
    this.setJsonLd(data.jsonLd ?? null);
  }

  /** Trang không tồn tại: HTTP 404 khi SSR (bot không lập chỉ mục trang lỗi) + noindex. */
  notFound(title: string, description: string): void {
    if (this.responseInit) {
      this.responseInit.status = 404;
    }
    this.set({ title, description, noindex: true });
  }

  private absolute(path: string): string {
    const origin = this.request ? new URL(this.request.url).origin : this.document.location?.origin ?? '';
    return origin + path;
  }

  private setCanonical(url: string | null): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!url) {
      link?.remove();
      return;
    }
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private setJsonLd(data: object | null): void {
    let script = this.document.getElementById(JSON_LD_ID);
    if (!data) {
      script?.remove();
      return;
    }
    if (!script) {
      script = this.document.createElement('script');
      script.id = JSON_LD_ID;
      script.setAttribute('type', 'application/ld+json');
      this.document.head.appendChild(script);
    }
    // Chặn đóng thẻ </script> sớm khi dữ liệu (tên/mô tả sản phẩm do seller nhập) chứa "<"
    script.textContent = JSON.stringify(data).replace(/</g, '\\u003c');
  }
}

/** Gọn khoảng trắng / xuống dòng để làm mô tả một dòng. */
function clean(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}
