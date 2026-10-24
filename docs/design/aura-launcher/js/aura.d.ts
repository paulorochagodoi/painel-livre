// Aura Launcher — vanilla JS API exposed as window.Aura (types are documentation).

export type Theme = 'light' | 'dark';
export type DoorId = 'fl' | 'fr' | 'rl' | 'rr';
export type PanelId = 'nav' | 'battery' | 'climate' | 'media';

export interface VehicleState {
  locked: boolean;
  /** true = open */
  doors: Record<DoorId, boolean>;
  /** 0–100, percent open */
  windows: Record<DoorId, number>;
  hood: boolean;
  trunk: boolean;
  port: 'closed' | 'open' | 'charging';
  portSide: 'rear-right' | 'rear-left';
}
export interface SeatState { heat: 0 | 1 | 2 | 3; vent: 0 | 1 | 2 | 3; }
export interface ClimateState {
  on: boolean; auto: boolean; ac: boolean; recirc: boolean; defF: boolean; defR: boolean; sync: boolean;
  /** °C, 16–30, step 0.5 */
  tL: number; tR: number;
  /** 1–7 */
  fan: number;
  seats: { d: SeatState; p: SeatState };
}
export interface MediaState { source: 'fm' | 'bt' | 'usb'; playing: boolean; idx: number; pos: number; vol: number; }
export interface BatteryState {
  soc: number; fuelKm: number; mode: 'ev' | 'hev'; drive: 'eco' | 'normal' | 'sport';
  hold: boolean; holdTarget: number; limit: 80 | 90 | 100; sched: boolean; v2l: boolean; v2lMin: number;
}
export interface LauncherState {
  model: string; outside: number;
  ui: { open: PanelId | null; search: boolean; searchSel: string | null; searchCat: string | null };
  vehicle: VehicleState; climate: ClimateState; media: MediaState; battery: BatteryState;
  nav: { active: string | null };
}
export interface Store {
  get(): LauncherState;
  /** mutate the state inside fn; every mounted part re-renders */
  update(fn: (s: LauncherState) => void): void;
  sub(fn: (s: LauncherState) => void): () => void;
}
export interface Mounted { store: Store; render(): void; destroy?(): void; }

export interface LauncherProps { store?: Store; }
export interface VehicleStatusProps { store?: Store; /** tap parts to toggle them (demo), default true */ interactive?: boolean; }
export interface ShortcutTilesProps { store?: Store; }
export interface DockProps { store?: Store; }
export interface StatusBarProps { store?: Store; }
export interface ClimatePanelProps { store?: Store; /** render without anchoring caret */ standalone?: boolean; }
export interface MediaPanelProps { store?: Store; standalone?: boolean; }
export interface NavPanelProps { store?: Store; standalone?: boolean; }
export interface NavSearchProps { store?: Store; standalone?: boolean; }
export interface BatteryPanelProps { store?: Store; standalone?: boolean; }

export interface AuraNamespace {
  createStore(init?: LauncherState): Store;
  defaults(): LauncherState;
  setTheme(t: Theme): void;
  getTheme(): Theme;
  icon(name: string, className?: string): string;
  iconNames: string[];
  mountLauncher(el: HTMLElement, props?: LauncherProps): { store: Store };
  mountVehicleStatus(el: HTMLElement, props?: VehicleStatusProps): Mounted;
  mountShortcutTiles(el: HTMLElement, props?: ShortcutTilesProps): Mounted;
  mountDock(el: HTMLElement, props?: DockProps): Mounted;
  mountStatusBar(el: HTMLElement, props?: StatusBarProps): Mounted;
  mountClimatePanel(el: HTMLElement, props?: ClimatePanelProps): Mounted;
  mountMediaPanel(el: HTMLElement, props?: MediaPanelProps): Mounted;
  mountNavPanel(el: HTMLElement, props?: NavPanelProps): Mounted;
  mountNavSearch(el: HTMLElement, props?: NavSearchProps): Mounted & { focus(): void };
  mountBatteryPanel(el: HTMLElement, props?: BatteryPanelProps): Mounted;
  mountIcons(el: HTMLElement): void;
}
declare global { interface Window { Aura: AuraNamespace; } }
