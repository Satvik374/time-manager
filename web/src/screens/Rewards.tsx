import { useState } from 'react'
import { Economy, Leveling, nextMilestone } from '../core/rules'
import { formatDuration, relativeTime, todayEpochDay } from '../core/time'
import { IconCoin, IconFire, IconPlus, IconTrash, IconTrophy } from '../components/Icons'
import { Card, Field, Modal, ProgressBar, StatTile } from '../components/Ui'
import { addCustomReward, buyFreezeToken, deleteReward, redeemReward } from '../data/mutations'
import { useAppState } from '../state/AppState'

export function RewardsScreen() {
  const { data, update, pushToast } = useAppState()
  const [showAdd, setShowAdd] = useState(false)

  const level = Leveling.infoFor(data.stats.xp)
  const milestone = nextMilestone(data.stats.currentStreak)
  const atRisk = data.stats.currentStreak > 0 && data.stats.lastStreakDay < todayEpochDay()
  const unlocked = data.achievements.filter((item) => item.unlockedAt != null).length
  const canBuyFreeze =
    data.stats.freezeTokens < Economy.MAX_FREEZE_TOKENS &&
    data.stats.coins >= Economy.FREEZE_TOKEN_COST

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Rewards</h1>
          <p className="page-subtitle">
            Level {level.level} · {level.title}
          </p>
        </div>
        <div className="page-actions">
          <span className="chip">
            <IconCoin size={15} /> {data.stats.coins} coins
          </span>
        </div>
      </header>

      <div className="split">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Card>
            <div className="streak-row">
              <div className={`streak-badge${atRisk ? ' risk' : ''}`}>
                <IconFire size={22} />
                <strong>{data.stats.currentStreak}</strong>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <h3 className="card-title">
                  {data.stats.currentStreak === 0
                    ? 'No streak yet'
                    : `${data.stats.currentStreak}-day streak`}
                </h3>
                <p className="muted small" style={{ marginTop: 2 }}>
                  {atRisk
                    ? `Finish ${Math.round(Economy.STREAK_COMPLETION_THRESHOLD * 100)}% of today's blocks to keep it alive.`
                    : data.stats.currentStreak === 0
                      ? `Complete ${Math.round(Economy.STREAK_COMPLETION_THRESHOLD * 100)}% of a day's blocks to start one.`
                      : `${Math.max(0, milestone - data.stats.currentStreak)} day(s) to the ${milestone}-day milestone.`}
                </p>
                <div style={{ marginTop: 'var(--space-3)' }}>
                  <ProgressBar
                    label={`Longest ${data.stats.longestStreak} days`}
                    trailing={`${data.stats.currentStreak}/${milestone}`}
                    progress={data.stats.currentStreak / milestone}
                    color="var(--accent)"
                  />
                </div>
              </div>
            </div>

            <div className="freeze-row">
              <div className="row">
                {Array.from({ length: Economy.MAX_FREEZE_TOKENS }, (_, index) => (
                  <span
                    key={index}
                    className="freeze-token"
                    style={{ opacity: index < data.stats.freezeTokens ? 1 : 0.22 }}
                  >
                    🧊
                  </span>
                ))}
                <span className="muted small" style={{ marginLeft: 8 }}>
                  Streak freezes cover one missed day automatically.
                </span>
              </div>
              <button
                type="button"
                className="btn primary"
                disabled={!canBuyFreeze}
                onClick={() => {
                  const result = buyFreezeToken(data)
                  if (result.ok) update(() => result.data)
                  pushToast(result.message, result.ok ? 'reward' : 'info')
                }}
              >
                Buy · {Economy.FREEZE_TOKEN_COST} 🪙
              </button>
            </div>
          </Card>

          <div>
            <div className="row" style={{ marginBottom: 'var(--space-3)' }}>
              <h2 className="section-title" style={{ margin: 0 }}>
                Reward shop
              </h2>
              <span className="spacer" />
              <button type="button" className="btn ghost" onClick={() => setShowAdd(true)}>
                <IconPlus size={15} /> Add your own
              </button>
            </div>
            <div className="grid cols-2">
              {data.rewards.map((reward) => {
                const affordable = data.stats.coins >= reward.cost
                return (
                  <div key={reward.id} className="reward-card">
                    <span className="reward-emoji">{reward.emoji}</span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 650 }}>{reward.title}</div>
                      {reward.description ? (
                        <div className="muted small">{reward.description}</div>
                      ) : null}
                      {reward.timesRedeemed > 0 ? (
                        <div className="muted small">
                          Claimed {reward.timesRedeemed}×
                          {reward.lastRedeemedAt
                            ? `, last ${relativeTime(reward.lastRedeemedAt)}`
                            : ''}
                        </div>
                      ) : null}
                    </div>
                    <div className="row">
                      {reward.isCustom ? (
                        <button
                          type="button"
                          className="btn subtle icon-only"
                          aria-label={`Delete ${reward.title}`}
                          onClick={() => update((current) => deleteReward(current, reward.id))}
                        >
                          <IconTrash size={15} />
                        </button>
                      ) : null}
                      <button
                        type="button"
                        className={`btn ${affordable ? 'primary' : 'ghost'}`}
                        disabled={!affordable}
                        onClick={() => {
                          const result = redeemReward(data, reward.id)
                          if (result.ok) update(() => result.data)
                          pushToast(result.message, result.ok ? 'reward' : 'info')
                        }}
                      >
                        {reward.cost} 🪙
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          <div>
            <h2 className="section-title">
              Achievements · {unlocked}/{data.achievements.length}
            </h2>
            <div className="grid cols-3">
              {data.achievements.map((achievement) => {
                const isUnlocked = achievement.unlockedAt != null
                return (
                  <div
                    key={achievement.key}
                    className={`achievement${isUnlocked ? ' unlocked' : ''}`}
                  >
                    <span className="achievement-emoji">{achievement.emoji}</span>
                    <div style={{ fontWeight: 650, fontSize: 14 }}>{achievement.title}</div>
                    <div className="muted small">{achievement.description}</div>
                    {isUnlocked ? (
                      <div className="achievement-unlocked">Unlocked</div>
                    ) : (
                      <>
                        <div className="bar" style={{ marginTop: 'auto', height: 5 }}>
                          <span
                            style={{
                              width: `${Math.min(100, (achievement.progress / achievement.target) * 100)}%`,
                            }}
                          />
                        </div>
                        <div className="muted small" style={{ marginTop: 4 }}>
                          {achievement.progress}/{achievement.target}
                        </div>
                      </>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        </div>

        <aside style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Card>
            <div className="level-head">
              <div className="level-ring">
                <IconTrophy size={26} />
              </div>
              <div>
                <div style={{ fontSize: 20, fontWeight: 700, letterSpacing: '-0.02em' }}>
                  Level {level.level}
                </div>
                <div className="muted small">{level.title}</div>
              </div>
            </div>
            <div style={{ marginTop: 'var(--space-4)' }}>
              <ProgressBar
                label={`${level.xpIntoLevel} / ${level.xpForNextLevel} XP`}
                trailing={`Level ${level.level + 1}`}
                progress={level.progress}
              />
            </div>
          </Card>

          <div className="grid cols-2">
            <StatTile
              label="Blocks done"
              value={String(data.stats.totalTasksCompleted)}
              caption="all time"
            />
            <StatTile
              label="Time focused"
              value={formatDuration(data.stats.totalFocusMinutes)}
              caption={`${data.stats.perfectDays} perfect days`}
              accent="var(--secondary)"
            />
          </div>

          <Card title="How earning works">
            <ul className="rules-list">
              <li>
                <strong>+{Economy.XP_TASK_NORMAL} XP</strong> and{' '}
                <strong>{Economy.COINS_TASK_NORMAL} coins</strong> per block, more for high priority
              </li>
              <li>
                <strong>+{Economy.XP_CHECKLIST_ITEM} XP</strong> per checklist step
              </li>
              <li>
                <strong>+{Economy.XP_PERFECT_DAY} XP</strong> for a day with everything done
              </li>
              <li>Milestone coin bonuses at 3, 7, 14, 30 and 100 days</li>
              <li>A free streak freeze every 7 days, up to {Economy.MAX_FREEZE_TOKENS}</li>
            </ul>
          </Card>
        </aside>
      </div>

      {showAdd ? (
        <AddRewardModal
          onClose={() => setShowAdd(false)}
          onAdd={(reward) => {
            update((current) => addCustomReward(current, reward))
            pushToast(`Added "${reward.title}" to your reward shop.`, 'reward')
            setShowAdd(false)
          }}
        />
      ) : null}
    </div>
  )
}

function AddRewardModal({
  onClose,
  onAdd,
}: {
  onClose: () => void
  onAdd: (reward: { title: string; description: string; emoji: string; cost: number }) => void
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [emoji, setEmoji] = useState('🎁')
  const [cost, setCost] = useState('100')

  return (
    <Modal
      title="Add a reward"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!title.trim()}
            onClick={() =>
              onAdd({
                title: title.trim(),
                description: description.trim(),
                emoji: emoji || '🎁',
                cost: Number(cost) || 100,
              })
            }
          >
            Add reward
          </button>
        </>
      }
    >
      <p className="muted small">
        Rewards work best when they're things you actually want and would otherwise feel guilty
        about.
      </p>
      <Field label="Reward">
        <input
          className="input"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Long walk with a podcast"
        />
      </Field>
      <Field label="Note (optional)">
        <input
          className="input"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </Field>
      <div className="grid cols-2">
        <Field label="Icon">
          <input
            className="input"
            value={emoji}
            maxLength={2}
            onChange={(event) => setEmoji(event.target.value)}
          />
        </Field>
        <Field label="Cost in coins">
          <input
            className="input"
            inputMode="numeric"
            value={cost}
            onChange={(event) => setCost(event.target.value.replace(/\D/g, '').slice(0, 4))}
          />
        </Field>
      </div>
    </Modal>
  )
}
