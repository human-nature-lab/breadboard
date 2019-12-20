enum BlockType {
  MULTIPLE_CHOICE("multiple-choice"),   // Checkbox choices
  CHOICE("choice"),                     // Button choices
  TEXT("text"),                         // Free text input question
  CONTENT("content")                    // A content block displayed as HTML
}

/**
 * A generic Block class used by all other Block types
 */
public class Block {
  def content, type
  def onFirst = {}      // Run the first time each player visits the page this block is on
  def onEnter = {}      // Run each time a player enters the page this block is on
  def onExit = {}       // Run each time a player exits the page this block is on
  Question (String type, String content) {
    this.type = type
    this.content = content
  }
  Question (String type, String content, Map closures) {
    this(type, content)
    if ("onEnter" in closures) {
      this.onEnter = closures.onEnter
    }
    if ("onExit" in closures) {
      this.onExit = closures.onExit
    }
    if ("onFirst" in closures) {
      this.onFirst = closures.onFirst
    }
  }
  
}

/**
 * A collection of questions shown to the player on one screen. 
 * TODO: Allow trivial randomization 
 * TODO: Allow trivial insertion of dummy questions w/ randomization
 * TODO: Allow addition of captchas
 */
public class Page {
  def blocks = []


  /**
   * Add a block directly to the 
   */
  def addBlock (Block block) {
    this.blocks << block
  }

  /**
   * Add a block to the form using a basic Map to describe it
   * @param {Map} block
   * @param block.type - A valid block type
   * @param {String}  [block.content] - The content for this block. The content purpose varies by Block type.
   * @param {Closure} [block.onInit]  - A closure which is run when the page is created. 
   * @param {Closure} [block.onExit]  - A closure which is run before a player leaves this page. Returns false to prevent navigation.
   * @param {Closure} [block.onEnter] - A closure which is run before a player enters this page. 
   */
  def addBlock (Map block) {

  }

}

public class Form {
  def pages = []
  def isStarted
  def players = []

  def addPlayer (Vertex player) {

  }

  def addPage (Page page) {
    this.pages << page
  }
  def addPage (List questions) {
    def page = new Page()
    questions.each{
      page.addQuestion(it)
    }
  }
}





def comprehensionTest = new Form()
def formPage = comprehensionTest.addPage([
  new Question
])