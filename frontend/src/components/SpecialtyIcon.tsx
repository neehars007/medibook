import { Baby, HeartPulse, Smile, Sparkles, Stethoscope, type LucideIcon } from 'lucide-react'

const map: Record<string, LucideIcon> = { stethoscope: Stethoscope, baby: Baby, sparkles: Sparkles, smile: Smile }

export default function SpecialtyIcon({ icon, className = 'h-5 w-5' }: { icon: string | null; className?: string }) {
  const Icon = (icon && map[icon]) || HeartPulse
  return <Icon className={className} aria-hidden />
}
