import { registrarIcones } from '@celke/kole-react';
import {
  Award,
  BookOpen,
  Bookmark,
  CirclePlay,
  GraduationCap,
  LayoutDashboard,
  Pencil,
  Pin,
  PinOff,
  Upload,
  Users,
} from 'lucide-react';

/** Ícones usados pelo nome nas telas e que a @celke/kole-react não registra por padrão. */
registrarIcones({
  award: Award,
  'book-open': BookOpen,
  bookmark: Bookmark,
  'circle-play': CirclePlay,
  'graduation-cap': GraduationCap,
  'layout-dashboard': LayoutDashboard,
  pencil: Pencil,
  pin: Pin,
  'pin-off': PinOff,
  upload: Upload,
  users: Users,
});
