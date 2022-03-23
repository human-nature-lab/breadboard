export function toHHMMSS (str: string): string {
  let ms_num = parseInt(str, 10) // don't forget the second parm
  if (ms_num < 0) return '00:00'

  let sec_num = Math.round(ms_num / 1000) // don't forget the second parm
  let hours: any = Math.floor(sec_num / 3600)
  let minutes: any = Math.floor((sec_num - (hours * 3600)) / 60)
  let seconds: any = sec_num - (hours * 3600) - (minutes * 60)

  if (hours < 10) {hours = '0' + hours}
  if (minutes < 10) {minutes = '0' + minutes}
  if (seconds < 10) {seconds = '0' + seconds}
  let time = minutes + ':' + seconds
  if (hours > 0) time = hours + ':' + time
  return time
}
