import java.util.Collections
import java.util.WeakHashMap

enum BlockType {
  CHOICE,
  HTML,
  TEXT,
  SCALE
  // CAPTCHA("captcha"),
  // RANDOM_LOGIC("random_logic")
}

enum TextType {
  TEXT,
  DECIMAL,
  INTEGER
}

public class FormBase {

  /**
   * Convert possible types of value / label combinations into a consistent value / content map
   * Values are passed in one by one and can resemble any of the following:
   * - just the value such as "2" or "Dog"
   * - just the value as a map such as [ value: "2" ] or [ value: "Dog" ]
   * - a value with the content such as [ value: "2", content: "Two" ] or [ value: 1, contentKey: "Dog" ]
   */
  private Map mapValueLabel (def val) {
    if (val in Map) {
      if (!("content" in val)) {
        val.content = val.value
      }
      return val
    } else {
      return [
        value: val,
        content: val
      ]
    }
  }

  /**
   * Get the randomization property if it has been provided
   */
  private Boolean getRandom (Map opts) {
    if ("randomize" in opts) {
      return opts["randomize"]
    } else {
      return false
    }
  }
}

FormBase.metaClass.fetchContent = { Map opts ->
  // TODO: Handle locale property
  if (!("content" in opts || "contentKey" in opts)) {
    throw new Exception("Must supply either the 'content' or 'contentKey' property")
  }
  String content = opts.get("content") ?: c.get(opts.contentKey)
  if ("fills" in opts) {
    content = c.interpolate(content, opts.fills)
  }
  return content
}

/**
 * Accessible alias for a.addEvent
 */
FormBase.metaClass.addEvent = { String name, Map data -> 
  a.addEvent(name, data)
}

public class Block extends FormBase {
  Closure onExit
  Closure onInit
  BlockType type
  Map content = [:] // Used to fetch content later

  Block (Map opts) {
    content.content = opts.content
    content.contentKey = opts.contentKey
    content.fills = opts.fills
  }

  private String getPlayerContent (Vertex player, Map opts) {
    // TODO: Handle player locale prop
    return this.fetchContent(opts)
  }

  /**
   * Method for assigning this block to the player. Allows for custom serialization
   */
  public Map assignTo (Vertex player) {
    return [
      type: this.type,
      content: this.getPlayerContent(player, this.content)
    ]
  }
}

public class Question extends Block {
  String name
  Boolean isRequired = false
  Question (Map opts) {
    super(opts)
    if (!("name" in opts)) {
      throw new Exception("Questions must have the 'name' property")
    }
    this.name = opts.name
    if ("required" in opts) {
      this.isRequired = opts.required
    }
  }

  public Map assignTo (Vertex player) {
    Map vals = super.assignTo(player)
    vals.name = this.name
    vals.isRequired = this.isRequired
    return vals
  }

  public Map formatResult (String form, String playerId, Map result) {
    return [
      name: this.name,
      type: this.type,
      form: form,
      player: playerId,
      value: result.value,
      createdAt: result.createdAt,
      updatedAt: result.updatedAt
    ]
  }

  public void saveResult (Map result) {
    this.addEvent("question-result", result)
  }
}

public class HTMLBlock extends Block {
  HTMLBlock (Map opts) {
    super(opts)
    this.type = BlockType.HTML
  }

  public Map assignTo (Vertex player) {
    Map vals = super.assignTo(player)
    return vals
  }
}

public class ChoiceQuestion extends Question {
  Boolean multiple = false
  Boolean isRandom
  def choices = []
  ChoiceQuestion (Map opts) {
    super(opts)
    this.type = BlockType.CHOICE
    if (!("choices" in opts)) {
      throw new Exception("Must include 'choices' list when creating a multiple choice question")
    }

    if ("multiple" in opts) {
      this.multiple = opts.multiple
    }

    this.isRandom = this.getRandom(opts)
    this.choices = opts.choices.collect{ this.mapValueLabel(it) }

  }

  /**
   * Mutate the result map before it is stored in the db
   */
  public Map formatResult (String form, String playerId, Map res) {
    def formattedRes = super.formatResult(form, playerId, res)
    formattedRes.multiple = this.multiple
    return formattedRes
  }

  public Map assignTo (Vertex player) {
    Map vals = super.assignTo(player)
    vals.multiple = this.multiple
    vals.choices = this.choices
    if (this.isRandom) {
      Collections.shuffle(vals.choices)
    }
    return vals
  }
}

public class TextQuestion extends Question {
  
  TextQuestion (Map opts) {
    super(opts)
    this.type = BlockType.TEXT
  }

  public Map assignTo (Vertex player) {
    Map vals = super.assignTo(player)
    return vals
  }

}

public class ScaleQuestion extends Question {
  
  def items = []
  def choices = []
  Boolean isRandom
  ScaleQuestion (Map opts) {
    super(opts)
    this.type = BlockType.SCALE
    this.items = opts.items.collect{ this.mapValueLabel(it) }
    this.choices = opts.choices.collect{ this.mapValueLabel(it) }
    this.isRandom = this.getRandom(opts)
  }

  public Map assignTo (Vertex player) {
    Map vals = super.assignTo(player)
    vals.type = BlockType.SCALE
    vals.items = this.items
    vals.choices = this.choices
    if (this.isRandom) {
      Collections.shuffle(vals.items)
    }
    return vals
  }
}

public class PageSection extends FormBase {
  def blocks = []
  Boolean isRandom = false

  PageSection () {}
  PageSection (Map opts) {
    this.isRandom = this.getRandom(opts)
    if ("blocks" in opts) {
      opts.blocks.each{
        this.addBlock(it)
      }
    } else if ("questions" in opts) {
      opts.questions.each{
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
      case BlockType.CHOICE:
        block = new ChoiceQuestion(blockDesc)
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

  public Map assignTo (Vertex player) {
    def blocks = this.blocks.collect{ it.assignTo(player) }
    if (this.isRandom) {
      Collections.shuffle(blocks)
    }
    return [
      blocks: blocks
    ]
  }

}

public class Page extends FormBase {
  def sections = []
  String title
  Boolean isRandom
  Closure onExit
  Closure onEnter

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
    if ("blocks" in opts || "questions" in opts) {
      this.addSection(opts)
    } else if ("sections" in opts) {
      opts.sections.each{
        this.addSection(it)
      }
    } else if ("groups" in opts) {
      opts.groups.each{
        this.addSection(it)
      }
    }
    if ("title" in opts) {
      this.title = opts.title
    }

    this.isRandom = this.getRandom(opts)
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

  /**
   * Validate the results for this page
   */
  public Boolean validate (Map results) {
    for (def section : this.sections) {
      for (def block : section.blocks) {
        // Filter out blocks that don't require responses responses
        if (block instanceof HTMLBlock) continue

        def result = results[block.name]
        if (block.isRequired && (!result || result.value == null)) {
          return false
        } else if (result && "value" in result && "validate" in block && !block.validate(result.value)) {
          return false
        }
      }
    }
    return true
  }

  /**
   * Store the page results in the DB
   */
  public void saveResults (String form, Vertex player, Map results) {
    for (def section : this.sections) {
      for (def block : section.blocks) {
        // Filter out blocks that don't require responses responses
        if (block instanceof HTMLBlock) continue
        def result = results[block.name]
        def playerId = player.id
        if (result && result.value != null) {
          def res = block.formatResult(form, playerId, result)
          block.saveResult(res)
        }
      }
    }
  }

  /**
   * Perform page exit actions
   * @param {Vertex} player - The current player
   * @param {Boolean} isForward - True if we're leaving the page by hitting the next button or "Done"
   */
  public exit (Vertex player, Boolean isForward) {
    if (this.onExit) {
      this.onExit(player, isForward)
    }
  }

  /**
   * Perform page enter actions
   * @param {Vertex} player - The current player
   * @param {Boolean} isForward - True if we're entering the page via the next button (or first entrance of the first page)
   */
  public enter (Vertex player, Boolean isForward) {
    if (this.onEnter) {
      this.onEnter(player, isForward)
    }
    // TODO: When to randomize things?
  }

  public Map assignTo (Vertex player) {
    def sections = this.sections.collect{ it.assignTo(player) }
    if (this.isRandom) {
      Collections.shuffle(sections)
    }
    return [
      sections: sections
    ]
  }
}

enum FormState {
  PENDING, STARTED, ENDED
}

public class Form extends FormBase {
  
  def players = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>()) // Use WeakReferences to players so that they can be garbage collected if removed from the graph
  String name  // The unique key used for this form
  String formsKey = "forms"
  Boolean showStepper = true
  Boolean isNonLinear = false
  Boolean recordResults = true
  Boolean recordNavigation = false
  Boolean isRandom
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
      throw new Exception("Form must have a unique name used to identify it")
    }
    this.name = opts.name
    if ("stepper" in opts) {
      this.showStepper = opts.stepper
    }
    if ("nonLinear" in opts) {
      this.isNonLinear = opts.nonLinear
    }
    if ("recordResults" in opts) {
      this.recordResults = opts.recordResults
    }
    if ("recordNav" in opts) {
      this.recordNavigation = opts.recordNav
    }
    this.isRandom = this.getRandom(opts)
  }

  public start () {
    if (!this.pages.size()) {
      throw new Exception("Add at least one page to the form before starting it")
      return
    }
    this.state = FormState.STARTED
    // Start the form for all assigned players
    this.players.each{
      this.startPlayer(it)
    }
  }

  public clear () {
    this.players = []
  }

  /**
   * End the form and perform cleanup for all players
   */
  public end () {
    for (def player: this.players) {
      this.endPlayer(player)
    }
    this.clear()
  }

  /**
   * Add a closure to run when the form is completed by a player
   * @param {Closure<Vertex>} cb - A closure which is passed the player who just completed the form
   */
  public onDone (Closure cb) {
    this.doneClosures << cb
  }

  /**
   * Add a player to this form
   */
  public addPlayer (Vertex player) {
    // Allow a player to be added to the form multiple times without side-effects
    if (this.players.contains(player)) return
    
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
    // Player must have already been removed
    if (!(this.formsKey in player.private)) {
      return
    }
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

    for (def cb : this.doneClosures) {
      cb(player)
    }
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
    
    // Randomize the page order per player
    def lastIndex = this.pages.size() - 1
    def pageOrder = (0..lastIndex).toList()
    if (this.isRandom) {
      Collections.shuffle(pageOrder)
    }
    println player.id + " page order " + pageOrder.toString()

    // Create the form state for this player
    def state = [
      location: [
        index: 0,
        size: this.pages.size()
      ],
      pages: pageOrder.collect{
        return [
          index: it,
          title: this.pages[it].title
        ]
      },
      results: []
    ]
    player.private[this.formsKey][this.name] = state
    this.attachPage(player, state)
  }

  private getPlayerState (Vertex player) {
    return player.private[this.formsKey][this.name]
  }

  private onPlayerNext (Vertex player, Map results) {

    if (this.recordNavigation) {
      this.addEvent("form-next", [
        form: this.name,
        player: player.id
      ])
    }

    def state = this.getPlayerState(player)
    
    // Run form validators
    def currentPage = this.getPlayerPage(player, state)
    if (!currentPage.validate(results)) {
      return this.sendNavError(player, [
        error: "Invalid responses"
      ])
    }

    if (this.recordResults) {
      currentPage.saveResults(this.name, player, results)
    }
    currentPage.exit(player, true)

    state.location.index++
    if (state.location.index < this.pages.size()) {
      def nextPage = this.attachPage(player, state)
      nextPage.enter(player, true)
    } else {
      println "form done " + player.id
      this.endPlayer(player)
    }
  }
  private onPlayerPrev (Vertex player, Map results) {
    def state = this.getPlayerState(player)
    if (this.recordNavigation) {
      this.addEvent("form-prev", [
        form: this.name,
        player: player.id
      ])
    }
    def currentPage = this.getPlayerPage(player, state)
    currentPage.exit(player, false)
    if (state.location.index > 0) {
      state.location.index--
      def prevPage = this.attachPage(player, state)
      prevPage.enter(player, false)
    }

  }
  private onPlayerSeek (Vertex player, Map results, Map dest) {
    def state = this.getPlayerState(player)
    println player.id + " seek"
  }

  /**
   * Lookup the page based on the players page order then apply that page state.
   */
  private Page attachPage (Vertex player, Map state) {
    def page = this.getPlayerPage(player, state)
    state.page = page.assignTo(player)
    return page
  }

  /**
   * Get the player's current page even when randomized
   */
  private Page getPlayerPage (Vertex player, Map state) {
    def pageIndex = state.pages[state.location.index].index
    return this.pages[pageIndex]
  }

  /**
   * Let the player know there was a navigation error
   * @param {Map} err - The error message
   */
  private void sendNavError (Vertex player, Map err) {
    player.send("f-" + this.name + '-e', err)
  }
}