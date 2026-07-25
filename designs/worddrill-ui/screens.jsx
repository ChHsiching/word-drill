// screens.jsx — Three screens: Drill, Library, Me
// Pure presentational components using CSS variables for theming.
// card.senses = [{ pos: 'v.', meaning: '放弃' }, { pos: 'n.', meaning: '放任' }, ...]

// Toggle switch — assigned to window at top level so React doesn't remount it
// (defining it inside MeScreen creates a new component type each render, losing CSS transitions)
const Toggle = ({ on, onClick }) => (
  <div className={`toggle-switch ${on ? 'on' : ''}`} onClick={(e) => { e.stopPropagation(); onClick(); }}>
    <div className="toggle-knob" />
  </div>
);

Object.assign(window, {

Toggle,



// ═══════════════════════════════════════════════════════════
// DRILL SCREEN — full-screen card, center, large type
// ═══════════════════════════════════════════════════════════
DrillScreen: function({ card, bookName, index, total, swipeHint, onSkip, hidePhonetic, locked, onToggleLock }) {
  const [animClass, setAnimClass] = React.useState('');
  const [prevIndex, setPrevIndex] = React.useState(index);
  React.useEffect(() => {
    if (index !== prevIndex) {
      setAnimClass('card-enter');
      setPrevIndex(index);
      const t = setTimeout(() => setAnimClass(''), 350);
      return () => clearTimeout(t);
    }
  }, [index, prevIndex]);

  return (
    <div className="screen" style={{
      flex: 1, display: 'flex', flexDirection: 'column',
      padding: '0 28px', position: 'relative',
    }}>
      {/* Top bar: book name (left) + skip + lock (right) — balanced, airy */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        paddingTop: 20,
      }}>
        <span style={{
          fontSize: 13, fontWeight: 500,
          color: 'var(--text-tertiary)', letterSpacing: 1,
        }}>{bookName}</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {onSkip && !locked && (
            <span onClick={onSkip} style={{
              fontSize: 13, fontWeight: 400,
              color: 'var(--text-tertiary)', cursor: 'pointer',
              padding: '4px 0 4px 12px',
              transition: 'opacity 0.15s',
            }}>跳过</span>
          )}
          {onToggleLock && (
            <div onClick={onToggleLock} style={{
              width: 30, height: 30, borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer',
              background: locked ? 'var(--text-primary)' : 'transparent',
              color: locked ? 'var(--bg)' : 'var(--text-tertiary)',
              transition: 'background var(--dur-normal) var(--spring-smooth), color var(--dur-normal) var(--spring-smooth), transform 0.15s var(--spring-snappy)',
            }} className="lock-toggle">
              {locked ? <window.IconUnlock size={16} /> : <window.IconLock size={16} />}
            </div>
          )}
        </div>
      </div>

      {/* Card index — minimal */}
      <div style={{
        textAlign: 'center', paddingTop: 8,
        fontSize: 11, fontWeight: 400,
        color: 'var(--text-tertiary)',
        fontVariantNumeric: 'tabular-nums',
      }}>{index + 1} / {total}</div>

      {/* Card center area */}
      <div style={{
        flex: 1, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
      }} className={animClass}>
        {/* English word — large, bold */}
        <div lang="en" style={{
          fontSize: 44, fontWeight: 600,
          color: 'var(--text-primary)',
          letterSpacing: '-0.5px', lineHeight: 1.1,
          textAlign: 'center',
        }}>{card.word}</div>

        {/* Phonetic — Charis SIL, hidden if setting enabled */}
        {card.phonetic && !hidePhonetic && (
          <div lang="en" style={{
            fontSize: 17, fontWeight: 400,
            color: 'var(--text-secondary)',
            marginTop: 10, letterSpacing: 0.5,
            fontFamily: '"Charis SIL", "Gentium Plus", "Doulos SIL", serif',
          }}>{card.phonetic}</div>
        )}

        {/* Divider */}
        <div style={{
          width: 32, height: 1,
          background: 'var(--separator-strong)',
          margin: '20px 0',
        }} />

        {/* Senses — pos + meaning on SAME line, one line per sense */}
        <div style={{
          display: 'flex', flexDirection: 'column', gap: 10,
          alignItems: 'center',
        }}>
          {card.senses.map((s, i) => (
            <div key={i} style={{
              display: 'flex', alignItems: 'baseline', gap: 8,
              flexWrap: 'wrap', justifyContent: 'center', maxWidth: 300,
            }}>
              <span style={{
                fontSize: 15, fontWeight: 400, fontStyle: 'italic',
                color: 'var(--text-tertiary)',
                fontFamily: '-apple-system, "SF Pro Text", system-ui, sans-serif',
              }}>{s.pos}</span>
              <span lang="zh" style={{
                fontSize: 22, fontWeight: 400,
                color: 'var(--text-primary)', lineHeight: 1.4,
                fontFamily: '"PingFang SC", -apple-system, "Microsoft YaHei", system-ui, sans-serif',
              }}>{s.meaning}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Swipe hint — bottom, subtle */}
      <div style={{
        textAlign: 'center', paddingBottom: 12,
        fontSize: 12, color: 'var(--text-tertiary)',
        opacity: swipeHint ? 1 : 0, transition: 'opacity 0.3s',
      }}>
        {swipeHint === 'last' ? '已经是最后一张' :
         swipeHint === 'first' ? '已经是第一张' :
         '← 左右滑动切换 →'}
      </div>
    </div>
  );
},

// ═══════════════════════════════════════════════════════════
// LIBRARY SCREEN — word book list
// ═══════════════════════════════════════════════════════════
LibraryScreen: function({ books, currentBookId, onSelectBook, onAddBook, onOpenBook }) {
  return (
    <div className="screen" style={{ padding: '0 0 20px' }}>
      {/* Large title */}
      <div style={{ padding: '8px 24px 24px' }}>
        <div style={{
          fontSize: 34, fontWeight: 700,
          color: 'var(--text-primary)', letterSpacing: '-0.8px',
        }}>词库</div>
      </div>

      {/* Book list — pure text, selected = solid inverted block (variant B) */}
      <div style={{ padding: '0 16px' }}>
        {books.map((book, i) => {
          const selected = book.id === currentBookId;
          return (
            <div key={i} onClick={() => onSelectBook(book.id)}
                 className={`book-item ${selected ? 'book-item-selected' : ''}`}
                 style={{
              display: 'flex', alignItems: 'center',
              padding: '14px 16px', borderRadius: 14,
              cursor: 'pointer', marginBottom: 4,
              background: selected ? undefined : 'transparent',
            }}>
              {/* Book name + subtitle */}
              <div style={{ flex: 1 }}>
                <div className="book-name" style={{
                  fontSize: 17, fontWeight: 500, lineHeight: 1.3,
                }}>{book.name}</div>
                <div className="book-sub" style={{
                  fontSize: 13, marginTop: 2,
                }}>{book.count.toLocaleString()} 词 · {book.isPreset ? '预置' : '自定义'}</div>
              </div>

              {/* Selected check — secondary color, no inversion */}
              {selected && (
                <div style={{ color: 'var(--text-secondary)' }}>
                  <window.IconCheck size={18} />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Add book button — floating-ish, clean */}
      <div style={{ padding: '20px 28px 0' }}>
        <div onClick={onAddBook} style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          padding: '15px', borderRadius: 14,
          background: 'var(--chip-bg)', cursor: 'pointer',
          fontSize: 16, fontWeight: 500, color: 'var(--text-primary)',
          transition: 'opacity 0.2s',
        }}>
          <window.IconPlus size={20} /> 新建词书
        </div>
      </div>
    </div>
  );
},

// ═══════════════════════════════════════════════════════════
// WORD LIST SCREEN — words inside a book (secondary)
// ═══════════════════════════════════════════════════════════
WordListScreen: function({ book, words, onBack }) {
  return (
    <div className="screen" style={{ padding: '0 0 20px' }}>
      {/* Back + title */}
      <div style={{
        display: 'flex', alignItems: 'center', padding: '4px 8px 16px',
      }}>
        <div onClick={onBack} style={{
          width: 40, height: 40, display: 'flex', alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer', color: 'var(--text-primary)',
        }}><window.IconChevronLeft size={22} /></div>
        <div style={{
          fontSize: 17, fontWeight: 600, color: 'var(--text-primary)',
        }}>{book.name}</div>
      </div>

      {/* Word list */}
      <div style={{ padding: '0 24px' }}>
        {words.map((w, i) => (
          <div key={i} style={{
            display: 'flex', alignItems: 'flex-start', gap: 12,
            padding: '14px 0', borderBottom: '0.5px solid var(--separator)',
          }}>
            <div lang="en" style={{
              fontSize: 16, fontWeight: 500, color: 'var(--text-primary)',
              minWidth: 80, flexShrink: 0,
            }}>{w.word}</div>
            <div style={{ flex: 1 }}>
              <div style={{
                fontSize: 13, color: 'var(--text-secondary)', fontStyle: 'italic',
              }}>{w.pos}</div>
              <div lang="zh" style={{
                fontSize: 15, color: 'var(--text-primary)', marginTop: 2, lineHeight: 1.4,
              }}>{w.meaning}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
},

// ═══════════════════════════════════════════════════════════
// ME SCREEN — stats + settings + data
// ═══════════════════════════════════════════════════════════
MeScreen: function({ stats, currentBook, theme, settings, onToggleSetting, onSetSetting, onExport, onImport }) {
  // Toggle is top-level (window.Toggle) so it doesn't remount and lose transitions

  return (
    <div className="screen" style={{ padding: '0 0 120px' }}>
      {/* Large title */}
      <div style={{ padding: '8px 24px 28px' }}>
        <div style={{
          fontSize: 34, fontWeight: 700,
          color: 'var(--text-primary)', letterSpacing: '-0.8px',
        }}>我的</div>
      </div>

      {/* Stats card — the hero of this screen */}
      <div style={{ padding: '0 16px 24px' }}>
        <div style={{
          background: 'var(--surface)', borderRadius: 20,
          padding: '28px 24px', boxShadow: 'var(--card-shadow)',
        }}>
          {/* Today count — biggest number */}
          <div style={{ textAlign: 'center' }}>
            <div lang="en" style={{
              fontSize: 56, fontWeight: 700,
              color: 'var(--text-primary)',
              fontVariantNumeric: 'tabular-nums', letterSpacing: '-1px',
              fontFamily: '-apple-system, "SF Pro Display", sans-serif',
            }}>{stats.today}</div>
            <div style={{
              fontSize: 13, fontWeight: 500,
              color: 'var(--text-secondary)', marginTop: 4,
              letterSpacing: 0.5,
            }}>今日刷卡</div>
          </div>

          {/* Progress bar — current book */}
          <div style={{ marginTop: 28 }}>
            <div style={{
              display: 'flex', justifyContent: 'space-between',
              marginBottom: 8, fontSize: 13, color: 'var(--text-secondary)',
            }}>
              <span>{currentBook.name}</span>
              <span style={{
                fontVariantNumeric: 'tabular-nums', color: 'var(--text-primary)', fontWeight: 500,
              }}>{stats.bookProgress.done} / {stats.bookProgress.total}</span>
            </div>
            <div style={{
              height: 6, borderRadius: 3,
              background: 'var(--progress-track)', overflow: 'hidden',
            }}>
              <div style={{
                height: '100%', borderRadius: 3,
                background: 'var(--text-primary)',
                width: `${(stats.bookProgress.done / stats.bookProgress.total * 100).toFixed(0)}%`,
                transition: 'width 0.6s cubic-bezier(0.22,1,0.36,1)',
              }} />
            </div>
            <div style={{
              fontSize: 12, color: 'var(--text-tertiary)', marginTop: 6,
              textAlign: 'right', fontVariantNumeric: 'tabular-nums',
            }}>{(stats.bookProgress.done / stats.bookProgress.total * 100).toFixed(0)}%</div>
          </div>

          {/* Total — secondary stat */}
          <div style={{
            display: 'flex', justifyContent: 'center',
            marginTop: 24, paddingTop: 20, borderTop: '0.5px solid var(--separator)',
          }}>
            <div style={{ textAlign: 'center' }}>
              <div lang="en" style={{
                fontSize: 24, fontWeight: 600, color: 'var(--text-primary)',
                fontVariantNumeric: 'tabular-nums',
              }}>{stats.total.toLocaleString()}</div>
              <div style={{
                fontSize: 12, color: 'var(--text-secondary)', marginTop: 2,
              }}>累计刷卡</div>
            </div>
          </div>
        </div>
      </div>

      {/* Settings — display group */}
      <div style={{ padding: '0 16px 16px' }}>
        <div style={{
          fontSize: 13, fontWeight: 500, color: 'var(--text-secondary)',
          padding: '0 12px 8px', letterSpacing: 0.3,
        }}>显示</div>
        <div style={{
          background: 'var(--surface)', borderRadius: 14, overflow: 'hidden',
        }}>
          {/* 隐藏音标 */}
          <div style={{ display: 'flex', alignItems: 'center', padding: '14px 16px' }}>
            <div style={{ flex: 1, fontSize: 16, color: 'var(--text-primary)' }}>隐藏音标</div>
            <Toggle on={settings.hidePhonetic} onClick={() => onToggleSetting('hidePhonetic')} />
          </div>
        </div>
      </div>

      {/* Settings — navigation group */}
      <div style={{ padding: '0 16px 16px' }}>
        <div style={{
          fontSize: 13, fontWeight: 500, color: 'var(--text-secondary)',
          padding: '0 12px 8px', letterSpacing: 0.3,
        }}>导航</div>
        <div style={{
          background: 'var(--surface)', borderRadius: 14, overflow: 'hidden',
        }}>
          {/* 导航栏风格 — click to cycle */}
          <div
            onClick={() => onSetSetting('navStyle', settings.navStyle === 'pill' ? 'bar' : 'pill')}
            style={{
              display: 'flex', alignItems: 'center', padding: '14px 16px', cursor: 'pointer',
            }}
          >
            <div style={{ flex: 1, fontSize: 16, color: 'var(--text-primary)' }}>导航栏风格</div>
            <div style={{ fontSize: 15, color: 'var(--text-secondary)' }}>
              {settings.navStyle === 'pill' ? '浮动胶囊' : '底部栏'}
            </div>
            <div style={{ marginLeft: 6, color: 'var(--text-tertiary)' }}>
              <window.IconChevronRight size={18} />
            </div>
          </div>
          <div style={{ height: 0.5, background: 'var(--separator)', margin: '0 16px' }} />
          {/* 简约导航（无字模式） */}
          <div style={{ display: 'flex', alignItems: 'center', padding: '14px 16px' }}>
            <div style={{ flex: 1, fontSize: 16, color: 'var(--text-primary)' }}>简约导航</div>
            <Toggle on={settings.compactNav} onClick={() => onToggleSetting('compactNav')} />
          </div>
        </div>
      </div>

      {/* Settings — general group */}
      <div style={{ padding: '0 16px' }}>
        <div style={{
          fontSize: 13, fontWeight: 500, color: 'var(--text-secondary)',
          padding: '0 12px 8px', letterSpacing: 0.3,
        }}>通用</div>
        <div style={{
          background: 'var(--surface)', borderRadius: 14, overflow: 'hidden',
        }}>
          {/* Theme row */}
          <div style={{
            display: 'flex', alignItems: 'center', padding: '14px 16px',
          }}>
            <div style={{ flex: 1, fontSize: 16, color: 'var(--text-primary)' }}>主题</div>
            <div style={{
              fontSize: 15, color: 'var(--text-secondary)',
            }}>{theme === 'light' ? '浅色' : theme === 'dark' ? '深色' : '跟随系统'}</div>
            <div style={{ marginLeft: 6, color: 'var(--text-tertiary)' }}>
              <window.IconChevronRight size={18} />
            </div>
          </div>
          <div style={{ height: 0.5, background: 'var(--separator)', margin: '0 16px' }} />
          {/* Export */}
          <div onClick={onExport} style={{
            display: 'flex', alignItems: 'center', padding: '14px 16px', cursor: 'pointer',
          }}>
            <window.IconDownload size={20} />
            <div style={{ flex: 1, marginLeft: 12, fontSize: 16, color: 'var(--text-primary)' }}>导出数据</div>
            <div style={{ color: 'var(--text-tertiary)' }}><window.IconChevronRight size={18} /></div>
          </div>
          <div style={{ height: 0.5, background: 'var(--separator)', margin: '0 16px' }} />
          {/* Import */}
          <div onClick={onImport} style={{
            display: 'flex', alignItems: 'center', padding: '14px 16px', cursor: 'pointer',
          }}>
            <window.IconUpload size={20} />
            <div style={{ flex: 1, marginLeft: 12, fontSize: 16, color: 'var(--text-primary)' }}>导入数据</div>
            <div style={{ color: 'var(--text-tertiary)' }}><window.IconChevronRight size={18} /></div>
          </div>
          <div style={{ height: 0.5, background: 'var(--separator)', margin: '0 16px' }} />
          {/* About */}
          <div style={{
            display: 'flex', alignItems: 'center', padding: '14px 16px', cursor: 'pointer',
          }}>
            <window.IconInfo size={20} />
            <div style={{ flex: 1, marginLeft: 12, fontSize: 16, color: 'var(--text-primary)' }}>关于</div>
            <div style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>v0.1.0</div>
          </div>
        </div>
      </div>
    </div>
  );
},

});
