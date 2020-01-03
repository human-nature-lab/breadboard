import java.util.Collections
import java.util.WeakHashMap

enum BlockType {
  MULTIPLE_SELECT,
  MULTIPLE_CHOICE,
  HTML,
  TEXT,
  SCALE
  // CAPTCHA("captcha"),
  // RANDOM_LOGIC("random_logic")
}

public class Block {
  Closure onExit
  Closure onInit
}

public class MultipleSelectQuestion extends Block {
  BlockType type = BlockType.MULTIPLE_SELECT
  MultipleSelectQuestion (Map opts) {

  }
}

public class HTMLBlock extends Block {
  BlockType type = BlockType.HTML
  String content
  HTMLBlock (Map opts) {
    if (!("content" in opts)) {
      throw Exception("Must supply the content property for an HTMLBlock")
    }
    this.content = opts.content
  }
}

public class MultipleChoiceQuestion extends Block {
  BlockType type = BlockType.MULTIPLE_CHOICE
  MultipleChoiceQuestion (Map opts) {

  }
}

public class TextQuestion extends Block {
  BlockType type = BlockType.TEXT
  String content
  TextQuestion (Map opts) {
    this.content = opts.content
  }
}

public class ScaleQuestion extends Block {
  BlockType type = BlockType.SCALE
  def questions = []
  def scale = []
  String content
  ScaleQuestion (Map opts) {
    this.content = opts.content
    this.questions = opts.questions
    this.scale = opts.scale
  }
}

public class PageSection {
  def blocks = []
  Boolean isStatic = false  // Indicates whether or not this section should be randomized

  PageSection () {}
  PageSection (Map opts) {
    if ("static" in opts) {
      this.isStatic = opts.static
    }
    if ("blocks" in opts) {
      opts.blocks.each{
        this.addBlock(it)
      }
    }
  }

  /**
   * Add a block instance directly to this page.
   * @param {Block} block - The block instance
   */
  def addBlock (Block block) {
    this.blocks << block
    return this
  }

  /**
   * Add a block via a Map. The block.type parameter is required for all types of blocks, but the other options will vary depending on the [BlockType].
   * @param {Map} block - A map which represents this block
   * @param {BlockType} block.type - The type of block to be used. See [BlockType] for supported types.
   */
  def addBlock (Map blockDesc) {
    Block block
    BlockType type = blockDesc.type.toUpperCase() as BlockType
    switch (type) {
      case BlockType.MULTIPLE_SELECT:
        block = new MultipleSelectQuestion(blockDesc)
        break
      case BlockType.MULTIPLE_CHOICE:
        block = new MultipleChoiceQuestion(blockDesc)
        break
      case BlockType.TEXT:
        block = new TextQuestion(blockDesc)
        break
      case BlockType.HTML:
        block = new HTMLBlock(blockDesc)
        break
      case BlockType.SCALE:
        block = new ScaleQuestion(blockDesc)
        break
    }
    this.addBlock(block)
    return this
  }

  /**
   * Rearrange the blocks in this section
   */
  public randomize () {
    if (this.isStatic) {
      return
    }
    Collections.shuffle(this.blocks)
  }

}

public class Page {
  def sections = []
  String title
  String description
  Boolean isStatic = true

  Page () {}
  /**
   * @param {Map} opts
   * @param {List<Block>} [opts.blocks] - A list of block descriptions to add. Cannot be used with the sections argument.
   * @param {List<FormSection>} [opts.sections] - A list of section descriptions to add. Cannot be used with the blocks argument.
   * @param {String} [opts.title] - A string which will be shown to the user when using the form stepper.
   * @param {Closure} [opts.onExit] - Closure which is called each time the page is left by a player.
   * @param {Closure} [opts.onEnter] - Closure which is called each time this page is entered by a player.
   */
  Page (Map opts) {
    if ("blocks" in opts) {
      this.addSection([
        blocks: opts.blocks
      ])
    } else if ("sections" in opts) {
      opts.sections.each{
        this.addSection(it)
      }
    }
    if ("title" in opts) {
      this.title = opts.title
    }
    // TODO: Handle other options
  }

  public addSection (PageSection section) {
    this.sections << section
    return section
  }

  public addSection () {
    return this.addSection(new PageSection())
  }

  public addSection (Map sectionDesc) {
    return this.addSection(new PageSection(sectionDesc))
  }
 
  public randomize () {
    this.sections.each{ it.randomize() }
  }

}

enum FormState {
  PENDING, STARTED, ENDED
}

public class Form {
  
  def players = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>()) // Use WeakReferences to players so that they can be garbage collected if removed from the graph
  String name  // The unique key used for this form
  String formsKey = "forms"
  Boolean useStepper = true
  Boolean isNonLinear = false
  def pages = []
  def doneClosures = []
  def state = FormState.PENDING

  /**
   * @param {Map} opts
   * @param {String} name - The unique key to use to identify this form
   * @param {Boolean} [stepper=true] - Indicates whether or not the progress through the form should be shown
   * @param {Boolean} [sequential=true] - Indicates whether the form can only be navigated in order (this is the default) or if the players can jump to different pages
   */
  Form (Map opts) {
    if (!("name" in opts)) {
      throw Exception("Form must have a unique name used to identify it")
    }
    this.name = opts.name
    if ("stepper" in opts) {
      this.useStepper = opts.stepper
    }
    if ("nonLinear" in opts) {
      this.isNonLinear = opts.nonLinear
    }
  }

  public start () {
    if (!this.pages.size()) {
      println "Add at least one page to the form before starting"
      return
    }
    this.state = FormState.STARTED
    // Start the form for all assigned players
    this.players.each{
      this.startPlayer(it)
    }
  }

  public end () {
    // TODO: End the form for all players
    // TODO: Call done closures
  }

  public onDone (Closure cb) {
    this.doneClosures << cb
  }

  /**
   * Randomize the order of pages
   */
  public randomize (Boolean randomizePageContents) {
    this.pages.each{ it.randomize() }
  }

  /**
   * Add a player to this form
   */
  public addPlayer (Vertex player) {
    this.players.add(player)
    if (this.state == FormState.STARTED) {
      this.startPlayer(player)
    }
  }

  public addPage (Page page) {
    this.pages << page
    return page
  }

  /**
   * Add a page to this form
   * @param {Map} page - A map which represents a form page. See Page constructor for all options.
   */
  public addPage (Map pageDesc) {
    def page = new Page(pageDesc)
    return this.addPage(page)
  }

  /**
   * Add multiple pages at the same time
   * @param {List<Map|Page>} pages - Either a list of Maps or Page objects
   */
  public addPages (List pages) {
    pages.each{
      this.addPage(it)
    }
    return this
  }

  /**
   * Ends the form for this player and cleanup resources
   */
  private endPlayer (Vertex player) {
    // Remove the state information for this form
    player.private[this.formsKey].remove(this.name)
    // If last form, remove forms object
    if (player.private[this.formsKey].size() == 0) {
      player.private.remove(this.formsKey)
    }
    // Remove form navigation listeners
    player.off("f-" + this.name + "-n")
    player.off("f-" + this.name + "-p")
    player.off("f-" + this.name + "-s")
  }

  /**
   * Assigns the form information to this player vertex
   */
  private startPlayer (Vertex player) {
    // Attach form navigation listeners
    player.on("f-" + this.name + "-n", this.&onPlayerNext)
    player.on("f-" + this.name + "-p", this.&onPlayerPrev)
    player.on("f-" + this.name + "-s", this.&onPlayerSeek)
    // Create multi-form map if it doesn't already exist
    if (!(this.formsKey in player.private)) {
      player.private[this.formsKey] = [:]
    }
    // Check if this form has already been started for this player
    if (this.name in player.private[this.formsKey]) {
      println "Form " + this.name + " already exists for this player"
    }
    // Create the form state for this player
    player.private[this.formsKey][this.name] = [
      location: [
        index: 0,
        size: this.pages.size()
      ],
      titles: this.pages.collect{ it.title },
      results: []
    ]
    this.attachPage(player)
  }

  private getPlayerState (Vertex player) {
    return player.private[this.formsKey][this.name]
  }

  private onPlayerNext (Vertex player, List results) {
    def state = this.getPlayerState(player)
    println player.id + " next"
  }
  private onPlayerPrev (Vertex player, List results) {
    def state = this.getPlayerState(player)
    println player.id + " prev"
  }
  private onPlayerSeek (Vertex player, List results, Map dest) {
    def state = this.getPlayerState(player)
    println player.id + " seek"
  }

  private attachPage (Vertex player) {
    def state = player.private[this.formsKey][this.name]
    def page = this.pages[state.location.index]
    state.page = [
      title: page.title,
      description: page.description,
      sections: page.sections
    ]
  }
}