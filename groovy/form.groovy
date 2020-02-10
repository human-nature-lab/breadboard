import java.util.Collections
import java.util.WeakHashMap
import java.util.HashMap

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

public class FormBase extends BreadboardBase {

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
  def answer
  Question (Map opts) {
    super(opts)
    if (!("name" in opts)) {
      throw new Exception("Questions must have the 'name' property")
    }
    this.name = opts.name
    if ("required" in opts) {
      this.isRequired = opts.required
    }
    if ("answer" in opts) {
      this.answer = opts.answer
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

  public Map assignTo (Vertex player, int seed) {
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

  public Map assignTo (Vertex player, int seed) {
    Map vals = super.assignTo(player)
    vals.multiple = this.multiple
    vals.choices = this.choices.collect()
    if (this.isRandom) {
      Collections.shuffle(vals.choices, new Random(seed))
    }
    return vals
  }

  public Boolean isCorrect (response) {
    if (this.multiple) {
      return this.answer.every{ a -> response.contains(a) }
    } else {
      return this.answer == response
    }
  }
}

public class TextQuestion extends Question {
  String inputType = "text"

  TextQuestion (Map opts) {
    super(opts)
    if ("inputType" in opts) {
      this.inputType = opts.inputType
    }
    this.type = BlockType.TEXT
  }

  public Map assignTo (Vertex player, int seed) {
    Map vals = super.assignTo(player)
    vals.inputType = this.inputType
    return vals
  }

  public Boolean isCorrect (val) {
    return this.answer == val
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

  public Map assignTo (Vertex player, int seed) {
    Map vals = super.assignTo(player)
    vals.type = BlockType.SCALE
    vals.items = this.items.collect()
    vals.choices = this.choices
    if (this.isRandom) {
      Collections.shuffle(vals.items, new Random(seed))
    }
    return vals
  }

  // Check that the answer has every correct value
  public Boolean isCorrect (List response) {
    return this.answer.every{ a -> response.contains(a) }
  }
}

public class PageSection extends FormBase {
  def blocks = []
  Boolean isRandom = false
  int subset = 0
  def questionDefaults = [:]

  PageSection () {}
  PageSection (Map opts) {
    this.isRandom = this.getRandom(opts)
    def keys = ["subset", "questionDefaults"]
    for (def key : keys) {
      if (key in opts) {
        this."${key}" = opts[key]
      }
    }
    if ("blocks" in opts) {
      for (int i = 0; i < opts.blocks.size(); i++) {
        this.addBlock(opts.blocks[i])
      }
    } else if ("questions" in opts) {
      for (int i = 0; i < opts.questions.size(); i++) {
        this.addBlock(opts.questions[i])
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
    blockDesc = this.questionDefaults + blockDesc
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

  public Map assignTo (Vertex player, int seed) {
    def blocks = []
    this.blocks.eachWithIndex{block, index -> 
      def b = block.assignTo(player, seed + index)
      b.index = index
      blocks << b
    }

    // Randomization is implied by the subset operation
    if (this.subset > 0) {
      blocks = this.randomBlocks(blocks.collect(), this.subset, new Random(seed))
    } else if (this.isRandom) {
      Collections.shuffle(blocks.collect(), new Random(seed))
    }
    return [
      blocks: blocks
    ]
  }
}

PageSection.metaClass.randomBlocks = { blocks, int n, Random r -> 
  return randomSubset(blocks, n, r)
}

public class Page extends FormBase {
  def sections = []
  String title
  Boolean isRandom
  Closure onExit
  Closure onEnter
  def sectionDefaults = [:]

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
    def keys = ["title", "onEnter", "onExit", "sectionDefaults"]
    for (def key : keys) {
      if (key in opts) {
        this."${key}" = opts[key]
      }
    }
    this.isRandom = this.getRandom(opts)
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
  }

  public addSection (PageSection section) {
    this.sections << section
    return section
  }

  public addSection () {
    return this.addSection(new PageSection(this.sectionDefaults))
  }

  public addSection (Map sectionDesc) {
    return this.addSection(new PageSection(this.sectionDefaults + sectionDesc))
  }

  /**
   * Validate the types of results for this page
   */
  public Boolean validate (Map results, Map state) {
    for (def section : state.page.sections) {
      def pageSection = this.sections[section.index]
      for (def blockRep : section.blocks) {
        def block = pageSection.blocks[blockRep.index]

        // Exclude blocks without data
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
   * Format and store the page results in the DB
   */
  public saveResults (String form, Vertex player, Map results) {
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
  }

  public Map assignTo (Vertex player, int seed) {
    def sections = []
    this.sections.eachWithIndex{ section, index ->
      def s = section.assignTo(player, seed + index)
      s.index = index
      sections << s
    }
    if (this.isRandom) {
      Collections.shuffle(sections, new Random(seed))
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
  def playerResults = new HashMap()
  def playerStates = new HashMap()
  String name  // The unique key used for this form
  String formsKey = "forms"
  Boolean showStepper = true
  Boolean isNonLinear = false
  Boolean recordResults = true
  Boolean recordNavigation = false
  Boolean efficient = false
  Boolean isRandom
  Random r = new Random()
  def pages = []
  def doneClosures = []
  def state = FormState.PENDING
  def pageDefaults = [
    sectionDefaults: [
      questionDefaults: [:]
    ]
  ]

  /**
   * @param {Map} opts
   * @param {String} name - The unique key to use to identify this form
   * @param {Boolean} [stepper=true] - Indicates whether or not the progress through the form should be shown
   * @param {Boolean} [nonLinear=false] - Indicates whether the form can only be navigated in order (this is the default) or if the players can jump to different pages
   * @param {Boolean} [recordResults=true] - Record an event for each page submission
   * @param {Boolean} [recordNav=false] - Record an event for each form navigation
   * @param {Map} [pageDefaults=[:]] - Sets default options for each page added to this form
   * @param {Map} [sectionDefaults=[:]] - Sets default options for each section added to this form
   * @param {Map} [questionDefaults=[:]] - Sets default options for each question added to this form
   */
  Form (Map opts) {
    if (!("name" in opts)) {
      throw new Exception("Form must have a unique name used to identify it")
    }
    this.name = opts.name
    def optsKeys = [
      stepper: "showStepper",
      efficient: "efficient",
      nonLinear: "nonLinear",
      recordResults: "recordResults",
      recordNav: "recordNavigation",
      pageDefaults: "pageDefaults"
    ]
    optsKeys.each{optKey, key -> 
      if (optKey in opts) {
        this."${key}" = opts[optKey]
      }
    }
    if ("sectionDefaults" in opts) {
      this.pageDefaults.sectionDefaults = opts.sectionDefaults
    }
    if ("questionDefaults" in opts) {
      this.pageDefaults.sectionDefaults.questionDefaults = opts.questionDefaults
    }
    this.isRandom = this.getRandom(opts)
  }

  /**
   * Start the form. Once this is called, the form will be shown to all added players and any players added
   * after the form has been started will automatically be shown the form.
   */
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

  /**
   * Clear out all players / results for this form
   */
  public clear () {
    this.players.clear()
    this.playerResults.clear()
  }

  /**
   * This method will calculate the percentage of correct responses for this player
   * @param {Vertex} player - The player to get the score for
   */
  public Map getScore (Vertex player) {
    def score = [
      correct: 0,
      incorrect: 0,
      total: 0,
      skipped: 0
    ]
    def results = this.playerResults.get(player.id)
    for (int i = 0; i < this.pages.size(); i++) {
      def pageResults = results.pages[i]
      def page = this.pages[i]
      for (def section : page.sections) {
        for (def block : section.blocks) {
          if (block instanceof HTMLBlock) continue
          if (!pageResults) {
            score.incorrect++
          } else if (block.answer != null) {
            if (!pageResults.containsKey(block.name)) {
              score.skipped++
            } else if (block.answer instanceof Closure && block.answer(pageResults[block.name])) {
              score.correct++
            } else if (pageResults[block.name] != null && block.isCorrect(pageResults[block.name])) {
              score.correct++
            } else {
              score.incorrect++
            }
          } else {
            score.skipped++
          }
          score.total++
        }
      }
    }
    return score
  }

  /**
   * Convert a score into a percentage. Takes into account skipped questions.
   * @param {Map} score - The score returned from the `getScore` method
   */
  public Double calculatePercent (Map score) {
    return score.total - score.skipped == 0 ? 1 : score.correct / (score.total - score.skipped) 
  }

  /**
   * End the form and perform cleanup for all players
   */
  public end () {
    for (def player: this.players) {
      this.endPlayer(player)
    }
  }

  /**
   * Add a closure to run when the form is completed by a player
   * @param {Closure<Vertex>} cb - A closure which is passed the player who just completed the form
   */
  public onDone (Closure cb) {
    this.doneClosures << cb
  }

  /**
   * Add multiple players to this form at once
   */
  public addPlayers (def players) {
    players.each{
      this.addPlayer(it)
    }
  }

  /**
   * Add a player to this form
   * @param {Vertex} player - The player to add
   */
  public addPlayer (Vertex player) {
    if (player == null) return // TODO: Why are null players being passed into the onJoinStep to begin with?
    // Allow a player to be added to the form multiple times without side-effects
    if (this.players.contains(player)) return
    
    this.players.add(player)
    if (this.state == FormState.STARTED) {
      this.startPlayer(player)
    }
  }

  /**
   * Add a single page instance
   */
  public addPage (Page page) {
    this.pages << page
    return page
  }

  /**
   * Add a page to this form
   * @param {Map} page - A map which represents a form page. See Page constructor for all options.
   */
  public addPage (Map pageDesc) {
    def page = new Page(this.pageDefaults + pageDesc)
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

  private endPlayer (Vertex player) {
    println "end player " + player.id
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
    player.off("f-next", this.&onPlayerNext)
    player.off("f-prev", this.&onPlayerPrev)
    player.off("f-seek", this.&onPlayerSeek)
    player.off("f-fetch", this.&onFetchState)

    for (def cb : this.doneClosures) {
      cb(player)
    }
  }

  /**
   * Assigns the form information to this player vertex
   */
  private startPlayer (Vertex player) {
    // Attach form navigation listeners
    player.on("f-next", this.&onPlayerNext)
    player.on("f-prev", this.&onPlayerPrev)
    player.on("f-seek", this.&onPlayerSeek)
    player.on("f-fetch", this.&onFetchState)
    // Create multi-form map if it doesn't already exist
    if (!(this.formsKey in player.private)) {
      player.private[this.formsKey] = [:]
    }
    // Check if this form has already been started for this player
    if (this.name in player.private[this.formsKey]) {
      println "Form " + this.name + " has already been assigned for this player"
    }
    
    // Randomize the page order per player
    def lastIndex = this.pages.size() - 1
    def pageOrder = (0..lastIndex).toList()
    if (this.isRandom) {
      Collections.shuffle(pageOrder)
    }

    // Create the form state for this player
    def state = [
      seed: this.r.nextInt(),
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
      efficient: this.efficient,
      showStepper: this.showStepper,
      nonLinear: this.isNonLinear,
      results: []
    ]
    if (this.efficient) {
      this.playerStates[player.id] = state
      player.private[this.formsKey][this.name] = [
        efficient: true
      ]
    } else {
      player.private[this.formsKey][this.name] = state
    }

    // Create the form results state for this player
    def playerResults = [
      pageOrder: pageOrder,
      pages: []
    ]
    this.playerResults[player.id] = playerResults

    // Compute the current page data for this player
    this.attachPage(player, state)
  }

  /**
   * Alias for accessing the form state for this player
   */
  private getPlayerState (Vertex player) {
    return this.efficient ? this.playerStates[player.id] : player.private[this.formsKey][this.name]
  }

  /**
   * Closure called when the player presses the next or done buttons
   */
  private onPlayerNext (Vertex player, Map data) {
    if (this.name != data.name) return
    def results = data.results
    if (this.recordNavigation) {
      this.addEvent("form-next", [
        form: this.name,
        player: player.id
      ])
    }

    def state = this.getPlayerState(player)
    println "next state " + state
    println "next results " + results

    // Run form validators
    Page currentPage = this.getPlayerPage(player, state)
    if (!currentPage.validate(results, state)) {
      return this.sendNavError(player, [
        error: "Invalid responses"
      ])
    }

    if (this.recordResults) {
      currentPage.saveResults(this.name, player, results)
    }

    def pageResults = this.playerResults[player.id].pages[state.pages[state.location.index].index]
    results.each{key, item ->
      pageResults[key] = item.value
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

  /**
   * Closure called when player presses the previous button
   */
  private onPlayerPrev (Vertex player, Map data) {
    if (this.name != data.name) return
    def results = data.results
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

  /**
   * Send the current form state to the player
   */
  private onFetchState (Vertex player, Map data) {
    if (data.name != this.name) return
    println "player get " + player.id
    if (!player) return
    if (!this.players.contains(player)) {
      return player.send("f-error", [
        form: this.name,
        error: "Not assigned to this form"
      ])
    }
    Map state = this.getPlayerState(player)
    println "fetch state " + state
    Page currentPage = this.getPlayerPage(player, state)
    player.send("f-fetch", [
      name: this.name,
      form: state,
    ])
  }

  /**
   * Closure called when the player seeks to a page
   */
  private onPlayerSeek (Vertex player, Map results, Map dest) {
    def state = this.getPlayerState(player)
    println player.id + " seek"
  }

  /**
   * Initialize the current page results if they haven't already been created
   */
  private void makePageResults (Vertex player, Map state) {
    // Initialize player results for this page
    def playerRes = this.playerResults[player.id]
    def pageResults = playerRes.pages[state.pages[state.location.index].index]
    if (!pageResults) {
      def storeResults = [:]
      for (def section : state.page.sections) {
        for (def block : section.blocks) {
          if (block.type != BlockType.HTML) {
            storeResults[block.name] = null
          }
        }
      }
      playerRes.pages[state.pages[state.location.index].index] = storeResults
    }
  }

  /**
   * Lookup the page based on the players page order then apply that page state.
   */
  private Page attachPage (Vertex player, Map state) {
    def page = this.getPlayerPage(player, state)
    state.page = page.assignTo(player, state.seed)
    this.makePageResults(player, state)
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
    player.send("f-error", [
      name: this.name,
      error: err
    ])
  }
}