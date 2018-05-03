import geb.Browser
import org.openqa.selenium.chrome.ChromeDriver

class BreadboardTestingTools {
  def random = new Random()
  def browsers = []
  def monkeyTimer = new Timer()

  def addTestPlayers(Integer n, String loginUrl, averageDelay = 100, delayDeviation = 50) {
    println("""Adding ${n} test players.""")
    for (i in 1..n) {
      def browser = new Browser(driver: new ChromeDriver())
      browser.go """https://localhost:9443/game/33/33/${i}/connected""".toString()
      browsers << browser
    }
    println ("""browsers ${browsers}""")
  }

  def start() {
    monkeyTimer.runEvery(100, 100) {
      for (browser in browsers) {
        browser("button", 0).click()
      }
    }
  }

  def cleanUp() {
    for (browser in browsers) {
      browser.quit()
    }
  }

}

TEST = new BreadboardTestingTools()