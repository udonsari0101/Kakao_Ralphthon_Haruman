import { useState } from 'react'
import React from 'react'

type Screen = 'splash' | 'home' | 'share' | 'detail' | 'decide' | 'skip-result' | 'buy-result' | 'history' | 'settings'
type Platform = 'coupang' | 'youtube' | 'instagram'
type Status = 'cooling' | 'ready' | 'bought' | 'skipped'
type Tab = 'vault' | 'history' | 'my'
type Filter = '전체' | '고민 중' | '결정 필요'

interface Product {
  id: string; name: string; price: number; platform: Platform; image: string
  status: Status; remaining: string; savedAt: string
}

const PRODUCTS: Product[] = [
  { id: '1', name: 'Sony WH-1000XM6', price: 519000, platform: 'coupang', image: 'photo-1505740420928-5e560c06d30e', status: 'cooling', remaining: '17시간 32분', savedAt: '어제' },
  { id: '2', name: 'Artemide Nessino Lamp', price: 190000, platform: 'instagram', image: 'photo-1507003211169-0a1dd7228f2d', status: 'ready', remaining: '', savedAt: '2일 전' },
  { id: '3', name: 'Mechanical Keyboard', price: 129000, platform: 'youtube', image: 'photo-1587829741301-dc798b83add3', status: 'cooling', remaining: '2시간 14분', savedAt: '오늘' },
  { id: '4', name: 'Dyson Airwrap Complete', price: 599000, platform: 'coupang', image: 'photo-1522338140262-f46f5913618a', status: 'cooling', remaining: '2일 남음', savedAt: '1일 전' },
  { id: '5', name: 'New Balance 990v6', price: 289000, platform: 'instagram', image: 'photo-1542291026-7eec264c27ff', status: 'ready', remaining: '', savedAt: '3일 전' },
]

const HISTORY_ITEMS = [
  { date: '9월 24일', name: 'Nike Air Max', price: 189000, action: 'skipped' as Status, image: 'photo-1542291026-7eec264c27ff' },
  { date: '9월 23일', name: 'Desk Lamp', price: 89000, action: 'bought' as Status, image: 'photo-1507473885765-e6ed057f782c' },
  { date: '9월 20일', name: 'iPad Stand', price: 49000, action: 'skipped' as Status, image: 'photo-1517336714731-489689fd1ca8' },
]

const fmt = (n: number) => `₩${n.toLocaleString('ko-KR')}`
const img = (id: string, w = 160, h = 160) => `https://images.unsplash.com/${id}?w=${w}&h=${h}&fit=crop&auto=format`
const platformLabel: Record<Platform, string> = { coupang: 'Coupang', youtube: 'YouTube', instagram: 'Instagram' }

function Icon({ name, size = 22 }: { name: 'back' | 'plus' | 'box' | 'history' | 'person' | 'chevron' | 'clock' | 'external' | 'check'; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.9, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
  if (name === 'back') return <svg {...common}><path d="m15 18-6-6 6-6"/></svg>
  if (name === 'plus') return <svg {...common}><path d="M12 5v14M5 12h14"/></svg>
  if (name === 'box') return <svg {...common}><rect x="3" y="5" width="18" height="15" rx="2"/><path d="M3 10h18M8 5V3m8 2V3"/></svg>
  if (name === 'history') return <svg {...common}><path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5M12 7v5l3 2"/></svg>
  if (name === 'person') return <svg {...common}><circle cx="12" cy="8" r="3.5"/><path d="M4.5 20c.7-3.7 3.3-5.5 7.5-5.5s6.8 1.8 7.5 5.5"/></svg>
  if (name === 'chevron') return <svg {...common}><path d="m9 18 6-6-6-6"/></svg>
  if (name === 'clock') return <svg {...common}><circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/></svg>
  if (name === 'external') return <svg {...common}><path d="M14 5h5v5M19 5l-8 8"/><path d="M17 13v5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h5"/></svg>
  return <svg {...common}><path d="m5 12 4.5 4.5L19 7"/></svg>
}

function StatusBar() {
  return <div className="flex h-7 items-center justify-between px-7 text-[12px] font-semibold text-txt-primary"><span>9:41</span><div className="flex items-center gap-1"><span className="h-2 w-3 rounded-sm border border-current"/><span className="h-2.5 w-2.5 rounded-full border-2 border-current"/><span className="h-2 w-5 rounded-sm bg-txt-primary"/></div></div>
}

function PlatformMeta({ platform }: { platform: Platform }) {
  return <span className="text-typo-7 text-txt-tertiary">{platformLabel[platform]}</span>
}

function Badge({ status, remaining }: { status: Status; remaining?: string }) {
  if (status === 'ready') return <span className="inline-flex shrink-0 items-center rounded-md bg-blue-50 px-2 py-1 text-typo-7 font-semibold text-blue-600">결정 필요</span>
  if (status === 'cooling') return <span className="inline-flex shrink-0 items-center gap-1 text-typo-7 font-medium text-blue-600"><Icon name="clock" size={13}/>{remaining}</span>
  if (status === 'bought') return <span className="inline-flex shrink-0 items-center rounded-md bg-grey-100 px-2 py-1 text-typo-7 font-medium text-txt-secondary">구매했어요</span>
  return <span className="inline-flex shrink-0 items-center rounded-md bg-green-50 px-2 py-1 text-typo-7 font-medium text-green-700">안 샀어요</span>
}

function ProductRow({ product, onClick, history }: { product: Product; onClick?: () => void; history?: boolean }) {
  return <button onClick={onClick} className="group flex min-h-[88px] w-full items-center gap-3 px-5 text-left transition-colors active:bg-grey-100">
    <div className="h-16 w-16 shrink-0 overflow-hidden rounded-lg bg-grey-100"><img src={img(product.image)} alt={product.name} className="h-full w-full object-cover" /></div>
    <div className="min-w-0 flex-1 py-3">
      <p className="text-typo-5 font-semibold leading-[1.4] text-txt-primary line-clamp-2">{product.name}</p>
      <p className="mt-1 text-typo-6 text-txt-secondary">{fmt(product.price)} <span className="text-grey-400">·</span> <PlatformMeta platform={product.platform}/></p>
    </div>
    <div className="flex shrink-0 flex-col items-end gap-2"><Badge status={product.status} remaining={product.remaining}/>{!history && product.status !== 'cooling' && <span className="text-typo-7 font-medium text-blue-600">결정하기 <span aria-hidden>›</span></span>}</div>
  </button>
}

function ListDivider() { return <div className="ml-[100px] h-px bg-grey-200"/> }

function FixedCTA({ children, double }: { children: React.ReactNode; double?: boolean }) {
  return <div className="shrink-0 border-t border-grey-200 bg-white px-5 pb-7 pt-3"><div className={double ? 'grid grid-cols-2 gap-2' : ''}>{children}</div></div>
}

function PrimaryButton({ children, onClick, className = '', disabled }: { children: React.ReactNode; onClick?: () => void; className?: string; disabled?: boolean }) {
  return <button onClick={onClick} disabled={disabled} className={`min-h-14 rounded-xl bg-blue-500 px-5 text-typo-5 font-semibold text-white transition-colors active:bg-blue-700 disabled:bg-grey-300 ${className}`}>{children}</button>
}

function WeakButton({ children, onClick, className = '' }: { children: React.ReactNode; onClick?: () => void; className?: string }) {
  return <button onClick={onClick} className={`min-h-14 rounded-xl bg-grey-100 px-5 text-typo-5 font-semibold text-txt-primary transition-colors active:bg-grey-200 ${className}`}>{children}</button>
}

function BottomNav({ tab, onChange }: { tab: Tab; onChange: (tab: Tab) => void }) {
  const items: { id: Tab; label: string; icon: 'box' | 'history' | 'person' }[] = [{ id: 'vault', label: '보관함', icon: 'box' }, { id: 'history', label: '기록', icon: 'history' }, { id: 'my', label: 'MY', icon: 'person' }]
  return <nav className="flex shrink-0 border-t border-grey-200 bg-white px-4 pb-6 pt-2">{items.map(item => <button key={item.id} onClick={() => onChange(item.id)} className={`flex min-h-12 flex-1 flex-col items-center justify-center gap-0.5 ${tab === item.id ? 'text-blue-600' : 'text-grey-500'}`}><Icon name={item.icon} size={21}/><span className="text-[11px] font-medium">{item.label}</span></button>)}</nav>
}

function SplashScreen({ onStart }: { onStart: () => void }) {
  return <div className="flex flex-1 flex-col bg-grey-50 px-6 pb-7 pt-6">
    <div className="flex flex-1 items-center justify-center"><div className="relative h-56 w-56"><div className="absolute inset-4 rounded-[44px] border border-dashed border-blue-200"/><div className="absolute left-0 top-7 w-28 rounded-xl bg-white p-3 shadow-[0_8px_24px_rgba(24,39,75,.06)]"><p className="text-typo-7 text-txt-tertiary">Instagram</p><p className="mt-1 text-typo-6 font-semibold">Nessino Lamp</p></div><div className="absolute bottom-6 right-0 w-28 rounded-xl bg-white p-3 shadow-[0_8px_24px_rgba(24,39,75,.06)]"><p className="text-typo-7 text-txt-tertiary">YouTube</p><p className="mt-1 text-typo-6 font-semibold">Desk setup</p></div><div className="absolute left-1/2 top-1/2 flex h-20 w-20 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-2xl bg-blue-500 text-white shadow-[0_10px_22px_rgba(49,130,246,.25)]"><Icon name="box" size={32}/></div></div></div>
    <div className="mb-8"><h1 className="text-typo-1 font-bold leading-[1.25] tracking-[-.035em] text-txt-primary">사고 싶은 마음,<br/>잠시 넣어두세요.</h1><p className="mt-4 text-typo-5 leading-7 text-txt-secondary">여러 곳에서 발견한 상품을 모아두고,<br/>조금 뒤 다시 결정해요.</p></div>
    <PrimaryButton onClick={onStart} className="w-full">시작하기</PrimaryButton>
  </div>
}

function HomeScreen({ onProduct, onDecide, onShare }: { onProduct: (p: Product) => void; onDecide: (p: Product) => void; onShare: () => void }) {
  const [filter, setFilter] = useState<Filter>('전체')
  const ready = PRODUCTS.filter(p => p.status === 'ready')
  const cooling = PRODUCTS.filter(p => p.status === 'cooling')
  const shown = filter === '전체' ? { ready, cooling } : filter === '결정 필요' ? { ready, cooling: [] } : { ready: [], cooling }
  return <div className="flex flex-1 flex-col overflow-hidden bg-grey-50">
    <header className="shrink-0 px-5 pb-4 pt-4"><div className="flex items-center justify-between"><h1 className="text-typo-3 font-bold tracking-[-.03em] text-txt-primary">보관함</h1><button onClick={onShare} aria-label="상품 저장" className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-500 text-white transition-colors active:bg-blue-700"><Icon name="plus" size={21}/></button></div><p className="mt-2 text-typo-6 text-txt-secondary">지금 12개의 소비를 고민하고 있어요</p><p className="mt-1 text-typo-7 text-txt-tertiary">이번 달 184,000원을 쓰지 않았어요</p>
      <div className="mt-5 flex rounded-xl bg-grey-100 p-1">{(['전체', '고민 중', '결정 필요'] as Filter[]).map(item => <button key={item} onClick={() => setFilter(item)} className={`min-h-9 flex-1 rounded-lg text-typo-7 font-semibold transition-colors ${filter === item ? 'bg-white text-txt-primary shadow-[0_1px_2px_rgba(0,0,0,.08)]' : 'text-txt-tertiary active:text-txt-primary'}`}>{item}</button>)}</div></header>
    <div className="flex-1 overflow-y-auto pb-7">
      {shown.ready.length > 0 && <section className="mb-7"><div className="px-5 pb-2"><h2 className="text-typo-4 font-bold text-txt-primary">다시 결정할 시간이에요</h2><p className="mt-1 text-typo-7 text-txt-tertiary">하루가 지난 상품이에요</p></div><div className="bg-white">{shown.ready.map((p, i) => <React.Fragment key={p.id}><ProductRow product={p} onClick={() => onDecide(p)}/>{i < shown.ready.length - 1 && <ListDivider/>}</React.Fragment>)}</div></section>}
      {shown.cooling.length > 0 && <section><div className="px-5 pb-2"><h2 className="text-typo-4 font-bold text-txt-primary">고민 중이에요</h2><p className="mt-1 text-typo-7 text-txt-tertiary">정해둔 시간 뒤에 다시 알려드릴게요</p></div><div className="bg-white">{shown.cooling.map((p, i) => <React.Fragment key={p.id}><ProductRow product={p} onClick={() => onProduct(p)}/>{i < shown.cooling.length - 1 && <ListDivider/>}</React.Fragment>)}</div></section>}
    </div>
  </div>
}

function ShareSheet({ onSave, onClose }: { onSave: (cooldown: string) => void; onClose: () => void }) {
  const [cooldown, setCooldown] = useState('내일')
  const [name, setName] = useState('Artemide Nessino Lamp')
  const [price, setPrice] = useState('190,000')
  const options = [{ key: '3시간', label: '3시간 뒤' }, { key: '내일', label: '내일' }, { key: '3일', label: '3일 뒤' }, { key: '7일', label: '일주일 뒤' }]
  const cta = cooldown === '내일' ? '내일 다시 보기' : `${cooldown} 뒤 다시 보기`
  return <div className="flex flex-1 flex-col bg-black/30" onClick={onClose}><div className="flex-1"/><section onClick={e => e.stopPropagation()} className="rounded-t-[28px] bg-white"><div className="mx-auto mt-3 h-1 w-9 rounded-full bg-grey-300"/><div className="px-5 pb-3 pt-5"><h2 className="text-typo-3 font-bold tracking-[-.025em] text-txt-primary">보관함에 넣을까요?</h2><p className="mt-2 text-typo-6 leading-6 text-txt-secondary">상품 정보를 확인하고 다시 볼 시간을 정해주세요.</p>
    <div className="mt-5 flex gap-3 rounded-xl bg-grey-50 p-3"><div className="h-16 w-16 shrink-0 overflow-hidden rounded-lg bg-grey-100"><img src={img('photo-1507003211169-0a1dd7228f2d')} alt="저장할 상품" className="h-full w-full object-cover"/></div><div className="min-w-0 flex-1"><input aria-label="상품명" value={name} onChange={e => setName(e.target.value)} className="w-full bg-transparent text-typo-5 font-semibold text-txt-primary outline-none"/><div className="mt-2 flex items-center gap-2"><span className="text-typo-7 text-txt-tertiary">Instagram</span><span className="text-grey-300">·</span><label className="flex items-center text-typo-6 text-txt-secondary">₩<input aria-label="가격" value={price} onChange={e => setPrice(e.target.value)} className="w-24 bg-transparent pl-1 outline-none"/></label></div></div></div>
    <fieldset className="mt-6"><legend className="text-typo-5 font-semibold text-txt-primary">언제 다시 볼까요?</legend><div className="mt-3 grid grid-cols-4 gap-2">{options.map(option => <button key={option.key} onClick={() => setCooldown(option.key)} className={`min-h-11 rounded-lg text-typo-7 font-semibold transition-colors ${cooldown === option.key ? 'bg-blue-500 text-white' : 'bg-grey-100 text-txt-secondary active:bg-grey-200'}`}>{option.label}</button>)}</div></fieldset>
  </div><FixedCTA><PrimaryButton onClick={() => onSave(cooldown)} className="w-full">{cta}</PrimaryButton><button onClick={() => onSave('')} className="mt-2 min-h-10 w-full text-typo-7 font-medium text-txt-tertiary">시간 정하지 않고 저장</button></FixedCTA></section></div>
}

function DetailScreen({ product, onBack, onDecide }: { product: Product; onBack: () => void; onDecide: () => void }) {
  const ready = product.status === 'ready'
  return <div className="flex flex-1 flex-col overflow-hidden bg-white"><header className="flex h-14 shrink-0 items-center px-4"><button aria-label="뒤로가기" onClick={onBack} className="flex h-11 w-11 items-center justify-center rounded-lg text-txt-primary active:bg-grey-100"><Icon name="back"/></button></header><main className="flex-1 overflow-y-auto px-5 pb-8"><div className="flex items-start gap-4 pt-3"><div className="h-20 w-20 shrink-0 overflow-hidden rounded-xl bg-grey-100"><img src={img(product.image, 200, 200)} alt={product.name} className="h-full w-full object-cover"/></div><div className="min-w-0"><h1 className="text-typo-3 font-bold leading-[1.35] tracking-[-.025em] text-txt-primary">{product.name}</h1><p className="mt-2 text-typo-6 text-txt-secondary">{fmt(product.price)} <span className="text-grey-400">·</span> {platformLabel[product.platform]}</p></div></div>
    <section className="mt-10"><p className="text-typo-6 text-txt-secondary">다시 생각하기까지</p><p className="mt-2 text-typo-timer font-bold leading-none tracking-[-.04em] text-txt-primary" style={{ fontVariantNumeric: 'tabular-nums' }}>{ready ? '지금' : product.remaining}</p><div className="mt-5 h-1.5 overflow-hidden rounded-full bg-blue-100"><div className="h-full w-[72%] rounded-full bg-blue-500"/></div><p className="mt-2 text-typo-7 text-txt-tertiary">지금까지 72% 지났어요</p></section>
    <section className="mt-10 border-y border-grey-200"><InfoRow label="저장한 날" value={`${product.savedAt} 저장`}/><InfoRow label="원본 플랫폼" value={platformLabel[product.platform]}/><InfoRow label="다시 보는 시간" value={ready ? '결정 필요' : product.remaining}/></section>
  </main><FixedCTA>{ready ? <PrimaryButton onClick={onDecide} className="w-full">결정하기</PrimaryButton> : <PrimaryButton onClick={() => {}} className="w-full"><span className="inline-flex items-center gap-2">원본 보기 <Icon name="external" size={17}/></span></PrimaryButton>}</FixedCTA></div>
}

function InfoRow({ label, value }: { label: string; value: string }) { return <div className="flex min-h-14 items-center justify-between gap-5"><span className="text-typo-6 text-txt-secondary">{label}</span><span className="text-typo-6 font-medium text-txt-primary">{value}</span></div> }

function DecideScreen({ product, onSkip, onBuy, onBack, onExtend }: { product: Product; onSkip: () => void; onBuy: () => void; onBack: () => void; onExtend: () => void }) {
  return <div className="flex flex-1 flex-col overflow-hidden bg-white"><header className="flex h-14 shrink-0 items-center px-4"><button aria-label="뒤로가기" onClick={onBack} className="flex h-11 w-11 items-center justify-center rounded-lg text-txt-primary active:bg-grey-100"><Icon name="back"/></button></header><main className="flex flex-1 flex-col overflow-y-auto px-5 pt-8 text-center"><div className="mx-auto h-24 w-24 overflow-hidden rounded-2xl bg-grey-100"><img src={img(product.image, 192, 192)} alt={product.name} className="h-full w-full object-cover"/></div><p className="mt-8 text-typo-6 text-txt-secondary">{product.savedAt}에 저장한 상품이에요</p><h1 className="mt-3 text-typo-1 font-bold leading-[1.28] tracking-[-.04em] text-txt-primary">하루가 지났어요.<br/>지금도 사고 싶나요?</h1><p className="mt-6 text-typo-5 font-semibold text-txt-primary">{product.name}</p><p className="mt-1 text-typo-6 text-txt-secondary">{fmt(product.price)} · {platformLabel[product.platform]}</p><button onClick={onExtend} className="mx-auto mt-8 min-h-11 text-typo-7 font-medium text-blue-600 active:text-blue-700">조금 더 고민할래요</button></main><FixedCTA double><WeakButton onClick={onSkip} className="w-full">안 살래요</WeakButton><PrimaryButton onClick={onBuy} className="w-full">살래요</PrimaryButton></FixedCTA></div>
}

function ResultScreen({ product, type, onHome }: { product: Product; type: 'skip' | 'buy'; onHome: () => void }) {
  const skip = type === 'skip'
  return <div className="flex flex-1 flex-col bg-white px-6"><div className="flex flex-1 flex-col items-center justify-center text-center"><div className={`flex h-14 w-14 items-center justify-center rounded-2xl ${skip ? 'bg-green-50 text-green-700' : 'bg-blue-50 text-blue-600'}`}><Icon name={skip ? 'check' : 'external'} size={27}/></div><h1 className="mt-7 text-typo-2 font-bold tracking-[-.04em] text-txt-primary">{skip ? '이번에는 사지 않기로 했어요' : '구매하기로 결정했어요'}</h1><p className="mt-3 text-typo-5 leading-7 text-txt-secondary">{skip ? `${fmt(product.price)}을 쓰지 않았어요.` : '원래 보던 곳에서 구매할 수 있어요.'}</p>{skip && <div className="mt-9 w-full rounded-xl bg-grey-50 px-5 py-4 text-left"><p className="text-typo-7 text-txt-tertiary">이번 달에는</p><p className="mt-1 text-typo-5 font-semibold text-txt-primary">총 313,000원을 쓰지 않았어요.</p></div>}</div><div className="pb-7">{skip ? <PrimaryButton onClick={onHome} className="w-full">보관함으로</PrimaryButton> : <><PrimaryButton onClick={() => {}} className="w-full">원본에서 보기</PrimaryButton><button onClick={onHome} className="mt-2 min-h-11 w-full text-typo-7 font-medium text-txt-secondary">구매했어요</button></>}</div></div>
}

function HistoryScreen() { return <div className="flex flex-1 flex-col overflow-hidden bg-grey-50"><header className="shrink-0 px-5 pb-5 pt-4"><h1 className="text-typo-3 font-bold tracking-[-.03em] text-txt-primary">소비 기록</h1><div className="mt-5 rounded-xl bg-white px-5 py-4"><p className="text-typo-7 text-txt-tertiary">이번 달</p><div className="mt-2 flex items-baseline justify-between"><p className="text-typo-5 text-txt-primary">24개 저장 · 11개 포기</p><p className="text-typo-6 font-semibold text-txt-primary">₩423,000</p></div><p className="mt-1 text-typo-7 text-txt-tertiary">쓰지 않은 금액이에요</p></div></header><main className="flex-1 overflow-y-auto"><h2 className="px-5 pb-2 text-typo-4 font-bold text-txt-primary">최근 결정</h2><div className="bg-white">{HISTORY_ITEMS.map((item, i) => { const p: Product = { ...item, id: String(i), platform: 'instagram', status: item.action, remaining: '', savedAt: item.date }; return <React.Fragment key={item.name}><ProductRow product={p} history/>{i < HISTORY_ITEMS.length - 1 && <ListDivider/>}</React.Fragment> })}</div></main></div> }

function SettingsScreen() { const [notif, setNotif] = useState(true); return <div className="flex flex-1 flex-col overflow-y-auto bg-grey-50 px-5 pt-4"><h1 className="text-typo-3 font-bold tracking-[-.03em] text-txt-primary">MY</h1><SettingsSection title="쿨다운"><SettingRow label="기본 다시 보기 시간" value="24시간"/></SettingsSection><SettingsSection title="알림"><div className="flex min-h-14 items-center justify-between bg-white px-5"><div><p className="text-typo-5 font-medium text-txt-primary">쿨다운 알림</p><p className="mt-1 text-typo-7 text-txt-tertiary">다시 볼 시간이 되면 알려드려요</p></div><button onClick={() => setNotif(!notif)} role="switch" aria-checked={notif} className={`relative h-8 w-[52px] rounded-full transition-colors ${notif ? 'bg-blue-500' : 'bg-grey-300'}`}><span className={`absolute top-1 h-6 w-6 rounded-full bg-white shadow-sm transition-transform ${notif ? 'translate-x-[22px]' : 'translate-x-1'}`}/></button></div></SettingsSection><SettingsSection title="데이터"><SettingRow label="내 기록 내보내기"/><SettingRow label="모든 기록 삭제" destructive/></SettingsSection></div> }
function SettingsSection({ title, children }: { title: string; children: React.ReactNode }) { return <section className="mt-8"><h2 className="mb-2 text-typo-7 font-semibold text-txt-tertiary">{title}</h2><div className="overflow-hidden rounded-xl bg-white">{children}</div></section> }
function SettingRow({ label, value, destructive }: { label: string; value?: string; destructive?: boolean }) { return <button className="flex min-h-14 w-full items-center justify-between border-b border-grey-200 px-5 text-left last:border-none active:bg-grey-50"><span className={`text-typo-5 font-medium ${destructive ? 'text-red-500' : 'text-txt-primary'}`}>{label}</span><span className="flex items-center gap-1 text-typo-6 text-txt-tertiary">{value}<Icon name="chevron" size={16}/></span></button> }

export default function App() {
  const [screen, setScreen] = useState<Screen>('splash')
  const [tab, setTab] = useState<Tab>('vault')
  const [selected, setSelected] = useState<Product | null>(null)
  const [toast, setToast] = useState('')
  const showNav = ['home', 'history', 'settings'].includes(screen)
  const flash = (message: string) => { setToast(message); window.setTimeout(() => setToast(''), 2200) }
  const changeTab = (next: Tab) => { setTab(next); setScreen(next === 'vault' ? 'home' : next === 'history' ? 'history' : 'settings') }
  let content: React.ReactNode
  if (screen === 'splash') content = <SplashScreen onStart={() => setScreen('home')}/>
  else if (screen === 'home') content = <HomeScreen onProduct={p => { setSelected(p); setScreen('detail') }} onDecide={p => { setSelected(p); setScreen('decide') }} onShare={() => setScreen('share')}/>
  else if (screen === 'share') content = <ShareSheet onClose={() => setScreen('home')} onSave={cooldown => { setScreen('home'); flash(cooldown ? `${cooldown === '내일' ? '내일' : `${cooldown} 뒤`} 다시 알려드릴게요` : '보관함에 넣었어요') }}/>
  else if (screen === 'detail' && selected) content = <DetailScreen product={selected} onBack={() => setScreen('home')} onDecide={() => setScreen('decide')}/>
  else if (screen === 'decide' && selected) content = <DecideScreen product={selected} onBack={() => setScreen('home')} onBuy={() => setScreen('buy-result')} onSkip={() => setScreen('skip-result')} onExtend={() => { setScreen('home'); flash('내일 다시 알려드릴게요') }}/>
  else if (screen === 'skip-result' && selected) content = <ResultScreen product={selected} type="skip" onHome={() => setScreen('home')}/>
  else if (screen === 'buy-result' && selected) content = <ResultScreen product={selected} type="buy" onHome={() => setScreen('home')}/>
  else if (screen === 'history') content = <HistoryScreen/>
  else content = <SettingsScreen/>
  return <div className="flex min-h-screen items-center justify-center bg-[#dfe2e8]"><div className="relative flex h-[844px] w-[390px] flex-col overflow-hidden rounded-[40px] bg-grey-50 shadow-[0_32px_80px_rgba(38,48,64,.24)]"><div className="flex h-9 shrink-0 justify-center bg-grey-50 pt-2"><div className="h-6 w-24 rounded-full bg-black"/></div><StatusBar/><div className="flex flex-1 flex-col overflow-hidden">{content}</div>{showNav && <BottomNav tab={tab} onChange={changeTab}/>} {toast && <div className="absolute bottom-24 left-5 right-5 rounded-xl bg-txt-primary px-4 py-3 text-center text-typo-7 font-medium text-white shadow-lg">{toast}</div>}</div></div>
}
