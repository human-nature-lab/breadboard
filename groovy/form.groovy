public class Question {
  Question (String type, Map opts) {

  }
}

public class Page {
  def questions = []

  def addQuestion (Question question) {

  }

}

public class Form {
  def pages = []

  def addPage (Page page) {
    this.pages << page
  }
  def addPage (List questions) {
    def page = new Page()
  }
}





def comprehensionTest = new Form()
def formPage = comprehensionTest.addPage([
  new Question
])