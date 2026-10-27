import { isPlatformBrowser } from '@angular/common';
import { computed, DestroyRef, effect, inject, Injectable, PLATFORM_ID, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, tap } from 'rxjs';
import { ChatMessage, ChatPush, Conversation } from '../api/api.models';
import { ChatApi } from '../api/chat.api';
import { AuthStore } from '../auth/auth.store';
import { RealtimeService } from '../realtime/realtime.service';

/**
 * Trạng thái widget chat: danh sách hội thoại (cả phía người mua và phía shop của tôi), hội thoại đang mở, tin nhắn,
 * "đã xem" của bên kia. Tin mới / đã xem đến qua kênh realtime.
 */
@Injectable({ providedIn: 'root' })
export class ChatStore {
  private readonly api = inject(ChatApi);
  private readonly auth = inject(AuthStore);

  readonly panelOpen = signal(false);
  readonly conversations = signal<Conversation[]>([]);
  readonly activeId = signal<number | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly hasMore = signal(false);
  readonly otherLastRead = signal<number | null>(null);
  readonly loadingMessages = signal(false);

  readonly active = computed(() => this.conversations().find((c) => c.id === this.activeId()) ?? null);
  readonly unreadTotal = computed(() => this.conversations().reduce((n, c) => n + c.unread, 0));

  constructor() {
    if (!isPlatformBrowser(inject(PLATFORM_ID))) {
      return;
    }
    effect(() => {
      const loggedIn = this.auth.isLoggedIn();
      untracked(() => (loggedIn ? this.loadConversations() : this.reset()));
    });
    inject(RealtimeService)
      .chat$.pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((push) => this.onPush(push));
  }

  toggle(): void {
    this.panelOpen.update((open) => !open);
    if (this.panelOpen()) {
      this.loadConversations();
    }
  }

  close(): void {
    this.panelOpen.set(false);
  }

  /** Nút "Chat ngay" ở trang sản phẩm / shop: mở hội thoại với shop. */
  openWithShop(shopId: number): Observable<Conversation> {
    return this.api.open(shopId).pipe(
      tap((c) => {
        this.conversations.update((list) => [c, ...list.filter((x) => x.id !== c.id)]);
        this.panelOpen.set(true);
        this.select(c.id);
      }),
    );
  }

  select(conversationId: number | null): void {
    this.activeId.set(conversationId);
    this.messages.set([]);
    this.otherLastRead.set(null);
    if (conversationId === null) {
      return;
    }
    this.loadingMessages.set(true);
    this.api.messages(conversationId).subscribe({
      next: (page) => {
        if (this.activeId() !== conversationId) {
          return;
        }
        this.messages.set(page.items);
        this.hasMore.set(page.hasMore);
        this.otherLastRead.set(page.otherLastReadMessageId);
        this.loadingMessages.set(false);
        this.markRead(conversationId);
      },
      error: () => this.loadingMessages.set(false),
    });
  }

  loadOlder(): void {
    const id = this.activeId();
    const oldest = this.messages()[0];
    if (!id || !oldest || !this.hasMore()) {
      return;
    }
    this.api.messages(id, oldest.id).subscribe((page) => {
      if (this.activeId() === id) {
        this.messages.update((list) => [...page.items, ...list]);
        this.hasMore.set(page.hasMore);
      }
    });
  }

  send(content: string): Observable<ChatMessage> {
    const id = this.activeId()!;
    return this.api.send(id, content).pipe(tap((m) => this.addMessage(m)));
  }

  /** Phía của tôi trong hội thoại đang mở (để căn tin của mình sang phải). */
  mySide(): 'BUYER' | 'SHOP' | null {
    return this.active()?.side ?? null;
  }

  private onPush(push: ChatPush): void {
    if (push.type === 'READ') {
      if (push.conversationId === this.activeId()) {
        this.otherLastRead.set(push.lastReadMessageId);
      }
      return;
    }
    const message = push.message!;
    const known = this.conversations().some((c) => c.id === push.conversationId);
    if (!known) {
      // Hội thoại mới (khách nhắn tới shop lần đầu): tải lại danh sách
      this.loadConversations();
      return;
    }
    const viewing = this.panelOpen() && this.activeId() === push.conversationId;
    this.conversations.update((list) => {
      const updated = list.map((c) =>
        c.id === push.conversationId
          ? { ...c, lastMessage: message.content, lastMessageAt: message.createdAt, unread: viewing ? 0 : (push.unread ?? c.unread) }
          : c,
      );
      return updated.sort((a, b) => (b.lastMessageAt ?? '').localeCompare(a.lastMessageAt ?? ''));
    });
    if (push.conversationId === this.activeId()) {
      this.addMessage(message);
      if (viewing && push.unread) {
        this.markRead(push.conversationId);
      }
    }
  }

  private addMessage(m: ChatMessage): void {
    if (m.conversationId !== this.activeId() || this.messages().some((x) => x.id === m.id)) {
      return;
    }
    this.messages.update((list) => [...list, m]);
  }

  private markRead(conversationId: number): void {
    this.conversations.update((list) => list.map((c) => (c.id === conversationId ? { ...c, unread: 0 } : c)));
    this.api.markRead(conversationId).subscribe({ error: () => undefined });
  }

  private loadConversations(): void {
    this.api.conversations().subscribe({ next: (page) => this.conversations.set(page.items), error: () => undefined });
  }

  private reset(): void {
    this.panelOpen.set(false);
    this.conversations.set([]);
    this.activeId.set(null);
    this.messages.set([]);
  }
}
