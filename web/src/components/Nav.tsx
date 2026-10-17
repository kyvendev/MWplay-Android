import type { ComponentType, SVGProps } from 'react';
import { routeHref, type Route } from '../app/router';
import { CompassIcon, HomeIcon, LibraryIcon, SettingsIcon } from './icons';
import { Logo } from './Logo';

const SECTIONS: { route: Route; label: string; icon: ComponentType<SVGProps<SVGSVGElement>> }[] = [
  { route: { name: 'home' }, label: 'Início', icon: HomeIcon },
  { route: { name: 'discover' }, label: 'Descobrir', icon: CompassIcon },
  { route: { name: 'library' }, label: 'Biblioteca', icon: LibraryIcon },
  { route: { name: 'settings' }, label: 'Configurações', icon: SettingsIcon },
];

/** Same four sections as the apps: bottom bar on phones, side rail on tablets, desktop and TV. */
export function Nav({ current }: { current: Route['name'] }) {
  return (
    <nav className="nav" aria-label="Navegação principal">
      <div className="nav__brand">
        <Logo />
      </div>
      {SECTIONS.map(({ route, label, icon: Icon }) => (
        <a key={route.name} className="nav__item focusable" href={routeHref(route)} aria-current={current === route.name ? 'page' : undefined}>
          <Icon />
          <span>{label}</span>
        </a>
      ))}
    </nav>
  );
}
