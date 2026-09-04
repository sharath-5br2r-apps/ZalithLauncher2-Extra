import { useState } from "react";
import { Check, Plus, Settings2, ChevronDown, User } from "lucide-react";

const profiles = [
  { name: "Survival", active: true },
  { name: "PvP", active: false },
  { name: "Performance", active: false },
];

function ProfileRow({
  profile,
  onSelect,
}: {
  profile: { name: string; active: boolean };
  onSelect: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  return (
    <button
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={onSelect}
      className={`
        w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left
        transition-all duration-150 group
        ${
          profile.active
            ? "bg-purple-500/20 text-purple-200"
            : hovered
            ? "bg-white/[0.06] text-zinc-200"
            : "text-zinc-400"
        }
      `}
    >
      {/* Selection indicator */}
      <span
        className={`
          flex-shrink-0 w-4 h-4 rounded-full border-2 flex items-center justify-center
          transition-all duration-200
          ${
            profile.active
              ? "border-purple-400 bg-purple-400"
              : "border-zinc-600 group-hover:border-zinc-400"
          }
        `}
      >
        {profile.active && <Check size={10} className="text-zinc-950" strokeWidth={3} />}
      </span>

      <span
        className={`flex-1 text-sm font-medium truncate tracking-[0.01em]`}
      >
        {profile.name}
      </span>

      {profile.active && (
        <span className="text-[10px] font-semibold uppercase tracking-wider text-purple-400 bg-purple-400/10 px-1.5 py-0.5 rounded-md">
          Active
        </span>
      )}
    </button>
  );
}

function Divider() {
  return <div className="h-px bg-white/[0.06] mx-1 my-1" />;
}

function ActionRow({
  icon: Icon,
  label,
  onClick,
}: {
  icon: React.ElementType;
  label: string;
  onClick?: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  return (
    <button
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={onClick}
      className={`
        w-full flex items-center gap-3 px-3 py-2 rounded-xl text-left
        transition-all duration-150
        ${hovered ? "bg-white/[0.06] text-zinc-200" : "text-zinc-500"}
      `}
    >
      <span className="w-4 h-4 flex items-center justify-center">
        <Icon size={15} />
      </span>
      <span className="text-sm font-medium">{label}</span>
    </button>
  );
}

export function ProfileDropdown() {
  const [profileList, setProfileList] = useState(profiles);

  const select = (name: string) =>
    setProfileList((p) => p.map((x) => ({ ...x, active: x.name === name })));

  return (
    <div className="min-h-screen bg-zinc-950 flex items-start justify-center pt-16 px-6">
      {/* Version card context — shows where dropdown originates */}
      <div className="w-[340px] space-y-3">
        {/* Simulated version card with profile button highlighted */}
        <div className="bg-zinc-900/80 border border-white/[0.07] rounded-2xl px-4 py-3 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/20 flex items-center justify-center flex-shrink-0">
            <span className="text-emerald-400 text-xs font-bold">F</span>
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-zinc-100 truncate">1.21.4 Fabric</p>
            <p className="text-xs text-zinc-500 truncate">Modrinth · 12 mods</p>
          </div>
          {/* Profile button — highlighted as active/open */}
          <div className="flex items-center gap-1">
            <button className="w-8 h-8 rounded-xl bg-purple-500/15 border border-purple-500/30 flex items-center justify-center">
              <User size={15} className="text-purple-400" />
            </button>
            <button className="w-8 h-8 rounded-xl hover:bg-white/5 flex items-center justify-center">
              <Settings2 size={15} className="text-zinc-500" />
            </button>
          </div>
        </div>

        {/* The dropdown */}
        <div
          className="
            bg-zinc-900/95 backdrop-blur-xl
            border border-white/[0.08]
            rounded-2xl shadow-2xl shadow-black/60
            overflow-hidden
          "
        >
          {/* Header */}
          <div className="px-4 pt-3.5 pb-2 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <User size={14} className="text-purple-400" />
              <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                Profiles
              </span>
            </div>
            <span className="text-[10px] text-zinc-600 bg-zinc-800/80 px-2 py-0.5 rounded-full">
              {profileList.length}
            </span>
          </div>

          {/* Profile list */}
          <div className="px-2 pb-1 space-y-0.5">
            {profileList.map((p) => (
              <ProfileRow key={p.name} profile={p} onSelect={() => select(p.name)} />
            ))}
          </div>

          <Divider />

          {/* Actions */}
          <div className="px-2 pb-2 space-y-0.5">
            <ActionRow icon={Plus} label="Create profile" />
            <ActionRow icon={Settings2} label="Manage profiles" />
          </div>
        </div>
      </div>
    </div>
  );
}
