import { useState } from "react";
import { Check, User, X } from "lucide-react";

const profiles = [
  { name: "Survival", active: true },
  { name: "PvP", active: false },
  { name: "Performance", active: false },
  { name: "Creative", active: false },
];

export function ProfilePanel() {
  const [list, setList] = useState(profiles);
  const [closing, setClosing] = useState(false);

  const select = (name: string) =>
    setList((p) => p.map((x) => ({ ...x, active: x.name === name })));

  const active = list.find((p) => p.active);

  return (
    <div className="min-h-screen bg-zinc-950 flex items-start justify-end pt-12 pr-6">
      {/* Right-side panel context */}
      <div className="w-[200px] space-y-3">

        {/* Simulated dashboard right rail — shows "Profile Panel replaces AccountAvatar" */}
        <div className="text-[10px] uppercase tracking-widest text-zinc-600 font-semibold text-center mb-1">
          Right Panel
        </div>

        {/* Profile Panel — replaces AccountAvatar */}
        <div
          className="
            bg-zinc-900/90 backdrop-blur-xl
            border border-white/[0.08]
            rounded-3xl overflow-hidden
            shadow-2xl shadow-black/50
          "
        >
          {/* Panel header */}
          <div className="px-4 pt-3.5 pb-2 flex items-center justify-between border-b border-white/[0.05]">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-lg bg-purple-500/20 flex items-center justify-center">
                <User size={12} className="text-purple-400" />
              </div>
              <span className="text-xs font-semibold text-zinc-300">Profiles</span>
            </div>
            <button className="w-5 h-5 rounded-lg hover:bg-white/10 flex items-center justify-center transition-colors">
              <X size={11} className="text-zinc-500" />
            </button>
          </div>

          {/* Profile list */}
          <div className="px-2 py-2 space-y-0.5">
            {list.map((profile) => (
              <button
                key={profile.name}
                onClick={() => select(profile.name)}
                className={`
                  w-full flex items-center gap-2.5 px-2.5 py-2 rounded-xl text-left
                  transition-all duration-150 group
                  ${
                    profile.active
                      ? "bg-purple-500/15 text-purple-200"
                      : "hover:bg-white/[0.05] text-zinc-400 hover:text-zinc-200"
                  }
                `}
              >
                {/* Indicator */}
                <span
                  className={`
                    flex-shrink-0 w-3 h-3 rounded-full border flex items-center justify-center
                    transition-all duration-200
                    ${
                      profile.active
                        ? "border-purple-400 bg-purple-400"
                        : "border-zinc-600 group-hover:border-zinc-400"
                    }
                  `}
                >
                  {profile.active && <Check size={7} className="text-zinc-950" strokeWidth={4} />}
                </span>

                <span className="flex-1 text-xs font-medium truncate">{profile.name}</span>
              </button>
            ))}
          </div>

          {/* Active profile chip at bottom */}
          <div className="px-3 pb-3 pt-1 border-t border-white/[0.05] mt-1">
            <div className="flex items-center gap-1.5 bg-purple-500/10 rounded-xl px-2.5 py-1.5">
              <div className="w-1.5 h-1.5 rounded-full bg-purple-400" />
              <span className="text-[10px] text-purple-300 font-medium truncate">
                {active?.name ?? "—"}
              </span>
            </div>
          </div>
        </div>

        {/* Spacer label */}
        <div className="text-[10px] uppercase tracking-widest text-zinc-700 font-semibold text-center mt-4">
          (tapping profile icon again closes)
        </div>

        {/* Gear icon below — shows spatial relationship */}
        <div className="flex justify-center gap-2 mt-2">
          <div className="w-8 h-8 rounded-xl bg-purple-500/15 border border-purple-500/25 flex items-center justify-center">
            <User size={15} className="text-purple-400" />
          </div>
          <div className="w-8 h-8 rounded-xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-zinc-500">
              <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </div>
        </div>
        <p className="text-[9px] text-zinc-700 text-center">Profile · Settings</p>
      </div>
    </div>
  );
}
