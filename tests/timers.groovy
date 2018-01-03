// Quick test of all the combinations of timers
def addTimers(){
    def timers = [
            [time: 600],
            [time: 400, type: "currency", currencyAmount: "200", appearance: "info"],
            [time: 300, type: "percent", appearance: "success"]
    ]

    timers.each {
        g.addTimer(it)
        def t = it
        t['direction'] = 'up'
        g.addTimer(t)
    }
}