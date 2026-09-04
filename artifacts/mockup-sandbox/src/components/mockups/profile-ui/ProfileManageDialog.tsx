import { useState } from "react";
import { Copy, Pencil, Trash2, Plus, User, X, ChevronRight } from "lucide-react";

type Profile = { id: number; name: string };

const initial: Profile[] = [
  { id: 1, name: "Survival" },
  { id: 2, name: "PvP" },
  { id: 3, name: "Performance" },
];

function ProfileItem({
  profile,
  isOnly,
  onDuplicate,
  onRename,
  onDelete,
}: {
  profile: Profile;
  isOnly: boolean;
  onDuplicate: () => void;
  onRename: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="group flex items-center gap-3 px-3 py-2.5 rounded-xl hover:bg-white/[0.04] transition-colors">
      {/* Avatar */}
      <div className="w-8 h-8 rounded-xl bg-zinc-800 border border-white/[0.06] flex items-center justify-center flex-shrink-0">
        <span className="text-xs font-bold text-zinc-400">
          {profile.name[0].toUpperCase()}
        </span>
      </div>

      {/* Name */}
      <span className="flex-1 text-sm font-medium text-zinc-200 truncate">
        {profile.name}
      </span>

      {/* Action buttons — visible on hover */}
      <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
        <ActionBtn icon={Copy} label="Duplicate" onClick={onDuplicate} />
        <ActionBtn icon={Pencil} label="Rename" onClick={onRename} />
        {!isOnly && (
          <ActionBtn icon={Trash2} label="Delete" onClick={onDelete} danger />
        )}
      </div>
    </div>
  );
}

function ActionBtn({
  icon: Icon,
  label,
  onClick,
  danger,
}: {
  icon: React.ElementType;
  label: string;
  onClick: () => void;
  danger?: boolean;
}) {
  const [hovered, setHovered] = useState(false);
  return (
    <button
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={onClick}
      title={label}
      className={`
        w-7 h-7 rounded-lg flex items-center justify-center transition-all
        ${
          danger
            ? hovered
              ? "bg-red-500/15 text-red-400"
              : "text-zinc-600"
            : hovered
            ? "bg-white/[0.08] text-zinc-300"
            : "text-zinc-600"
        }
      `}
    >
      <Icon size={13} />
    </button>
  );
}

// Rename inline input
function RenameOverlay({
  name,
  onConfirm,
  onCancel,
}: {
  name: string;
  onConfirm: (v: string) => void;
  onCancel: () => void;
}) {
  const [value, setValue] = useState(name);
  return (
    <div className="absolute inset-0 z-20 bg-zinc-900/95 rounded-2xl flex flex-col items-center justify-center p-6 gap-4">
      <p className="text-sm font-semibold text-zinc-200">Rename Profile</p>
      <input
        autoFocus
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && onConfirm(value)}
        className="w-full bg-zinc-800 border border-white/10 rounded-xl px-3.5 py-2.5 text-sm text-zinc-100 outline-none focus:border-purple-500/50 focus:ring-1 focus:ring-purple-500/30 transition-all"
      />
      <div className="flex gap-2 w-full">
        <button
          onClick={onCancel}
          className="flex-1 py-2 rounded-xl text-sm font-medium text-zinc-400 bg-zinc-800/80 hover:bg-zinc-800 transition-colors"
        >
          Cancel
        </button>
        <button
          onClick={() => onConfirm(value)}
          className="flex-1 py-2 rounded-xl text-sm font-medium text-white bg-purple-600/80 hover:bg-purple-600 transition-colors"
        >
          Save
        </button>
      </div>
    </div>
  );
}

export function ProfileManageDialog() {
  const [profiles, setProfiles] = useState(initial);
  const [renaming, setRenaming] = useState<number | null>(null);
  const [nextId, setNextId] = useState(4);

  const duplicate = (id: number) => {
    const src = profiles.find((p) => p.id === id)!;
    setProfiles((p) => [...p, { id: nextId, name: `${src.name} Copy` }]);
    setNextId((n) => n + 1);
  };

  const remove = (id: number) =>
    setProfiles((p) => p.filter((x) => x.id !== id));

  const rename = (id: number, name: string) => {
    setProfiles((p) => p.map((x) => (x.id === id ? { ...x, name } : x)));
    setRenaming(null);
  };

  const addNew = () => {
    setProfiles((p) => [...p, { id: nextId, name: `Profile ${nextId}` }]);
    setNextId((n) => n + 1);
  };

  return (
    <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
      {/* Dialog */}
      <div
        className="
          relative w-[380px]
          bg-zinc-900/95 backdrop-blur-xl
          border border-white/[0.08]
          rounded-3xl shadow-2xl shadow-black/70
          overflow-hidden
        "
      >
        {renaming !== null && (
          <RenameOverlay
            name={profiles.find((p) => p.id === renaming)?.name ?? ""}
            onConfirm={(v) => rename(renaming, v)}
            onCancel={() => setRenaming(null)}
          />
        )}

        {/* Header */}
        <div className="flex items-center justify-between px-5 pt-5 pb-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-2xl bg-purple-500/15 border border-purple-500/20 flex items-center justify-center">
              <User size={17} className="text-purple-400" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-zinc-100">Manage Profiles</h2>
              <p className="text-xs text-zinc-500">1.21.4 Fabric</p>
            </div>
          </div>
          <button className="w-8 h-8 rounded-xl hover:bg-white/[0.07] flex items-center justify-center transition-colors">
            <X size={15} className="text-zinc-500" />
          </button>
        </div>

        {/* Subtle divider */}
        <div className="h-px bg-white/[0.05] mx-5" />

        {/* Profile list */}
        <div className="px-3 py-2 space-y-0.5">
          {profiles.map((profile) => (
            <ProfileItem
              key={profile.id}
              profile={profile}
              isOnly={profiles.length === 1}
              onDuplicate={() => duplicate(profile.id)}
              onRename={() => setRenaming(profile.id)}
              onDelete={() => remove(profile.id)}
            />
          ))}
        </div>

        {/* Add new profile */}
        <div className="h-px bg-white/[0.05] mx-5 mt-1" />
        <div className="px-3 py-3">
          <button
            onClick={addNew}
            className="
              w-full flex items-center gap-3 px-3 py-2.5 rounded-xl
              text-purple-400 hover:text-purple-300
              hover:bg-purple-500/10
              transition-all duration-150 group
            "
          >
            <div className="w-8 h-8 rounded-xl bg-purple-500/10 border border-purple-500/20 flex items-center justify-center flex-shrink-0 group-hover:bg-purple-500/15 transition-colors">
              <Plus size={15} />
            </div>
            <span className="text-sm font-medium">Create new profile</span>
          </button>
        </div>

        {/* Footer */}
        <div className="px-5 pb-4">
          <button className="w-full py-2.5 rounded-xl text-sm font-semibold text-zinc-300 bg-zinc-800/80 hover:bg-zinc-800 border border-white/[0.05] transition-colors">
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
