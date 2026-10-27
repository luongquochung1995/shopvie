import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/** Aura với màu chủ đạo tím #9900CC (đồng bộ với --color-brand-* trong tailwind.css). */
export const ShopviPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#faf2fc',
      100: '#f3e0f9',
      200: '#e4bdf2',
      300: '#d18ce8',
      400: '#b647da',
      500: '#9900cc',
      600: '#8400af',
      700: '#6e0093',
      800: '#590076',
      900: '#45005c',
      950: '#2b0039',
    },
  },
});
