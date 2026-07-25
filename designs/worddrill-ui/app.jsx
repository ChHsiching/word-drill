// app.jsx — Main app: state, navigation, theme toggle
// Renders two device frames side-by-side (light + dark) for comparison.

// ── Mock data — senses array: [{ pos, meaning }] + phonetic ──
const SAMPLE_CARDS = [
  { word: 'abandon', phonetic: '/əˈbændən/', senses: [
    { pos: 'v.', meaning: '放弃；抛弃' },
    { pos: 'n.', meaning: '放任；狂热' },
  ]},
  { word: 'benefit', phonetic: '/ˈbenɪfɪt/', senses: [
    { pos: 'n.', meaning: '利益；好处；福利' },
    { pos: 'v.', meaning: '有益于；获益' },
  ]},
  { word: 'capable', phonetic: '/ˈkeɪpəbl/', senses: [
    { pos: 'adj.', meaning: '有能力的；能干的' },
  ]},
  { word: 'define', phonetic: '/dɪˈfaɪn/', senses: [
    { pos: 'v.', meaning: '定义；明确' },
  ]},
  { word: 'essential', phonetic: '/ɪˈsenʃl/', senses: [
    { pos: 'adj.', meaning: '必要的；本质的' },
    { pos: 'n.', meaning: '必需品；基本要素' },
  ]},
];

const SAMPLE_BOOKS = [
  { id: 1, name: 'CET-4', count: 4523, isPreset: true },
  { id: 2, name: 'CET-6', count: 6022, isPreset: true },
  { id: 3, name: '考研英语', count: 5548, isPreset: true },
  { id: 4, name: '我的生词本', count: 36, isPreset: false },
];

const SAMPLE_WORDS = [
  { word: 'abandon', pos: 'v.', meaning: '放弃；抛弃' },
  { word: 'benefit', pos: 'n.', meaning: '利益；好处' },
  { word: 'capable', pos: 'adj.', meaning: '有能力的；能干的' },
];

// ── Single device: full interactive prototype ──
function WordDrillDevice({ theme: forcedTheme }) {
  const [tab, setTab] = React.useState('drill'); // drill | library | me
  const [cardIndex, setCardIndex] = React.useState(0);
  const [currentBook, setCurrentBook] = React.useState(SAMPLE_BOOKS[0]);
  const [swipeHint, setSwipeHint] = React.useState('');
  const [settings, setSettings] = React.useState({
    hidePhonetic: false,
    navStyle: 'pill', // 'pill' | 'bar'
    compactNav: false,
  });
  const [locked, setLocked] = React.useState(false);

  const toggleSetting = (key) => setSettings(s => ({ ...s, [key]: !s[key] }));
  const [navFading, setNavFading] = React.useState(false);
  const setSetting = (key, val) => {
    if (key === 'navStyle') {
      // Fade out current → swap class → measure & set indicator (no transition) → fade in
      setNavFading(true);
      setTimeout(() => {
        setSettings(s => ({ ...s, navStyle: val }));
        // After swap, measure indicator position immediately without transition
        requestAnimationFrame(() => {
          const navEl = navRef.current;
          if (navEl && val !== 'bar') {
            const items = Array.from(navEl.querySelectorAll('.nav-item'));
            const activeItem = items.find(it => it.classList.contains('active'));
            if (activeItem) {
              const navRect = navEl.getBoundingClientRect();
              const itemRect = activeItem.getBoundingClientRect();
              setIndicatorStyle({
                left: itemRect.left - navRect.left,
                width: itemRect.width,
                noTransition: true,
              });
            }
          }
          setNavFading(false);
        });
      }, 200);
    } else {
      setSettings(s => ({ ...s, [key]: val }));
    }
  };

  // Sliding indicator — measures active nav item position
  const navRef = React.useRef(null);
  const [indicatorStyle, setIndicatorStyle] = React.useState({ left: 0, width: 0, noTransition: false });
  const tabs = ['drill', 'library', 'me'];

  React.useLayoutEffect(() => {
    const updateIndicator = () => {
      if (navFading) return; // skip during style swap — setSetting handles it
      const navEl = navRef.current;
      if (!navEl) return;
      const items = Array.from(navEl.querySelectorAll('.nav-item'));
      const activeItem = items.find(it => it.classList.contains('active'));
      if (activeItem) {
        const navRect = navEl.getBoundingClientRect();
        const itemRect = activeItem.getBoundingClientRect();
        setIndicatorStyle({
          left: itemRect.left - navRect.left,
          width: itemRect.width,
          noTransition: false, // normal tab switches DO want transition
        });
      }
    };
    requestAnimationFrame(() => requestAnimationFrame(updateIndicator));
    window.addEventListener('resize', updateIndicator);
    return () => window.removeEventListener('resize', updateIndicator);
  }, [tab, settings.compactNav, settings.navStyle, navFading]);
  const [subScreen, setSubScreen] = React.useState(null); // null | 'wordlist'

  const theme = forcedTheme; // forced by parent (light or dark)
  const isDark = theme === 'dark';

  const nextCard = () => {
    if (cardIndex >= SAMPLE_CARDS.length - 1) {
      setSwipeHint('last');
      setTimeout(() => setSwipeHint(''), 1500);
    } else {
      setCardIndex(cardIndex + 1);
      setSwipeHint('');
    }
  };
  const prevCard = () => {
    if (cardIndex <= 0) {
      setSwipeHint('first');
      setTimeout(() => setSwipeHint(''), 1500);
    } else {
      setCardIndex(cardIndex - 1);
      setSwipeHint('');
    }
  };

  // Keyboard navigation for demo
  React.useEffect(() => {
    const handler = (e) => {
      if (tab !== 'drill') return;
      if (e.key === 'ArrowRight') nextCard();
      if (e.key === 'ArrowLeft') prevCard();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [tab, cardIndex]);

  const stats = {
    today: 47,
    total: 3291,
    bookProgress: { done: 1247, total: currentBook.count },
  };

  return (
    <div className="device" data-theme={theme}>
      {/* Status bar */}
      <div className="status-bar">
        <div className="notch"></div>
      </div>

      {/* Theme toggle (design only) */}
      {forcedTheme === undefined && null}

      {/* Content */}
      <div className="content">
        {tab === 'drill' && (
          <window.DrillScreen
            card={SAMPLE_CARDS[cardIndex]}
            bookName={currentBook.name}
            index={cardIndex}
            total={SAMPLE_CARDS.length}
            swipeHint={swipeHint}
            hidePhonetic={settings.hidePhonetic}
            locked={locked}
            onToggleLock={() => setLocked(l => !l)}
            onSkip={() => { if (cardIndex < SAMPLE_CARDS.length - 1) setCardIndex(cardIndex + 1); }}
          />
        )}
        {tab === 'library' && !subScreen && (
          <window.LibraryScreen
            books={SAMPLE_BOOKS}
            currentBookId={currentBook.id}
            onSelectBook={(id) => { setCurrentBook(SAMPLE_BOOKS.find(b => b.id === id)); }}
            onAddBook={() => {}}
            onOpenBook={null}
          />
        )}
        {tab === 'library' && subScreen === 'wordlist' && (
          <window.WordListScreen book={currentBook} words={SAMPLE_WORDS} onBack={() => setSubScreen(null)} />
        )}
        {tab === 'me' && (
          <window.MeScreen
            stats={stats}
            currentBook={currentBook}
            theme={theme}
            settings={settings}
            onToggleSetting={toggleSetting}
            onSetSetting={setSetting}
            onExport={() => {}}
            onImport={() => {}}
          />
        )}
      </div>

      {/* Navigation — single element, fade-out→swap→fade-in on style change */}
      <div className="nav-pill-wrapper">
        <div
          ref={navRef}
          className={`bottom-nav ${settings.navStyle === 'bar' ? 'bar-style' : ''} ${tab === 'drill' && locked ? 'nav-hidden' : ''} ${navFading ? 'fading' : ''} ${settings.compactNav ? 'compact' : ''}`}
        >
          <div className="nav-indicator" style={{ left: indicatorStyle.left, width: indicatorStyle.width, display: settings.navStyle === 'bar' ? 'none' : 'block', transition: indicatorStyle.noTransition ? 'none' : undefined }} />
          <div className={`nav-item ${tab === 'drill' ? 'active' : ''}`} onClick={() => { setTab('drill'); setSubScreen(null); setLocked(false); }}>
            <window.IconDrill size={22} /><span className="nav-label">刷</span>
          </div>
          <div className={`nav-item ${tab === 'library' ? 'active' : ''}`} onClick={() => { setTab('library'); setSubScreen(null); setLocked(false); }}>
            <window.IconLibrary size={22} /><span className="nav-label">库</span>
          </div>
          <div className={`nav-item ${tab === 'me' ? 'active' : ''}`} onClick={() => { setTab('me'); setSubScreen(null); setLocked(false); }}>
            <window.IconMe size={22} /><span className="nav-label">我的</span>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── App: light + dark side-by-side ──
function App() {
  return (
    <>
      <div className="device-wrapper">
        <WordDrillDevice theme="light" />
        <div className="device-label">浅色 · Light</div>
      </div>
      <div className="device-wrapper">
        <WordDrillDevice theme="dark" />
        <div className="device-label">深色 · Dark</div>
      </div>
    </>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
