import { registrarIcones } from '@celke/kole-react';
import {
  Award,
  BookOpen,
  CirclePlay,
  GraduationCap,
  LayoutDashboard,
  Pencil,
  Upload,
  Users,
} from 'lucide-react';

/** Ícones usados pelo nome nas telas e que a @celke/kole-react não registra por padrão. */
registrarIcones({
  award: Award,
  'book-open': BookOpen,
  'circle-play': CirclePlay,
  'graduation-cap': GraduationCap,
  'layout-dashboard': LayoutDashboard,
  pencil: Pencil,
  upload: Upload,
  users: Users,
});
