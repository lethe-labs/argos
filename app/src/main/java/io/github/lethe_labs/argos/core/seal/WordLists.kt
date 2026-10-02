package io.github.lethe_labs.argos.core.seal

/**
 * Concrete, easy-to-picture nouns: recognising them later should not take
 * effort. Written for this project rather than borrowed, so there is no
 * third-party license attached. Each list must hold exactly 256 distinct
 * words, so that one HMAC byte maps to one word without bias.
 */
object WordLists {
    const val FRENCH = "fr"
    const val ENGLISH = "en"

    fun forLanguage(language: String): List<String> = if (language == FRENCH) FR else EN

    fun languageFor(isoLanguage: String): String = if (isoLanguage == FRENCH) FRENCH else ENGLISH

    val EN: List<String> = listOf(
        "acorn", "anchor", "apple", "apron", "arrow", "attic", "badge", "bagel",
        "bamboo", "banana", "banjo", "barrel", "basket", "beach", "beacon", "beaver",
        "bell", "bench", "berry", "bicycle", "bison", "blanket", "bottle", "bracelet",
        "bread", "breeze", "brick", "bridge", "broom", "bubble", "bucket", "butter",
        "button", "cabin", "cactus", "camel", "camera", "candle", "canoe", "canyon",
        "carpet", "carrot", "castle", "cedar", "cello", "chalk", "cherry", "chess",
        "cliff", "clock", "cloud", "clover", "coconut", "comet", "compass", "copper",
        "coral", "cotton", "crayon", "cricket", "crown", "crystal", "cupboard", "curtain",
        "cushion", "daisy", "desert", "diamond", "dolphin", "donkey", "dragon", "drum",
        "eagle", "easel", "elbow", "engine", "falcon", "feather", "fence", "fern",
        "ferry", "fiddle", "flag", "flame", "flute", "forest", "fossil", "fountain",
        "fox", "frog", "galaxy", "garden", "garlic", "giraffe", "glacier", "globe",
        "glove", "goat", "grape", "guitar", "hammer", "harbor", "harp", "hazel",
        "helmet", "heron", "honey", "horizon", "horse", "iceberg", "igloo", "island",
        "jacket", "jaguar", "jasmine", "jelly", "jigsaw", "kayak", "kettle", "kitten",
        "kiwi", "koala", "ladder", "lagoon", "lantern", "lemon", "leopard", "lettuce",
        "lighthouse", "lily", "lion", "lizard", "lobster", "locket", "lotus", "magnet",
        "mango", "maple", "marble", "meadow", "melon", "mirror", "mitten", "monkey",
        "moose", "mosaic", "moth", "mountain", "muffin", "mushroom", "napkin", "needle",
        "nest", "nutmeg", "oasis", "ocean", "octopus", "olive", "onion", "orange",
        "orchid", "otter", "owl", "oyster", "paddle", "palace", "panda", "paper",
        "parrot", "peach", "peanut", "pearl", "pebble", "pelican", "pencil", "penguin",
        "pepper", "piano", "pigeon", "pillow", "pinecone", "planet", "plum", "pocket",
        "pony", "potato", "prism", "puffin", "pumpkin", "puzzle", "quail", "quilt",
        "rabbit", "radish", "rainbow", "raven", "reef", "ribbon", "river", "robin",
        "rocket", "rose", "saddle", "salmon", "sandal", "satchel", "scarf", "seashell",
        "sheep", "shovel", "silver", "sled", "sloth", "snail", "snowflake", "sparrow",
        "spider", "spinach", "sponge", "spoon", "squirrel", "stable", "starfish", "statue",
        "stove", "strawberry", "sugar", "sunflower", "swan", "sweater", "table", "teapot",
        "tiger", "timber", "tomato", "torch", "tortoise", "tower", "tractor", "trumpet",
        "tulip", "turnip", "turtle", "umbrella", "valley", "vanilla", "velvet", "violin",
        "volcano", "wagon", "walnut", "walrus", "whale", "willow", "window", "zebra",
    )

    val FR: List<String> = listOf(
        "abeille", "abricot", "aigle", "aiguille", "amande", "ananas", "ancre", "anguille",
        "arbre", "ardoise", "armoire", "artichaut", "asperge", "autruche", "avion", "baleine",
        "balcon", "ballon", "bambou", "banane", "banjo", "barque", "bateau", "beignet",
        "belette", "berceau", "betterave", "biche", "bijou", "biscuit", "bison", "blaireau",
        "bobine", "bocal", "bougie", "bouilloire", "boussole", "bouteille", "bouton", "brebis",
        "brindille", "brioche", "brouette", "cabane", "cactus", "cafetière", "caillou", "canard",
        "canoë", "carotte", "carrosse", "cartable", "casque", "castor", "cerise", "cerf",
        "chameau", "champignon", "chandelle", "chapeau", "chaton", "chaudron", "chemin", "cheval",
        "chèvre", "chouette", "cigale", "citron", "citrouille", "clairière", "clarinette", "clocher",
        "coccinelle", "colibri", "collier", "comète", "concombre", "coquelicot", "coquillage", "corail",
        "corbeau", "cornichon", "coton", "coussin", "crabe", "crayon", "crocodile", "cygne",
        "dauphin", "dentelle", "désert", "diamant", "dindon", "domino", "dragon", "écharpe",
        "échelle", "écureuil", "éléphant", "émeraude", "enclume", "épinard", "éponge", "escargot",
        "étoile", "éventail", "faucon", "fenouil", "fenêtre", "fougère", "fourmi", "framboise",
        "fromage", "fusée", "galet", "gazelle", "girafe", "glacier", "glaçon", "gondole",
        "gorille", "grenier", "grenouille", "guitare", "hamac", "hamster", "harpe", "hérisson",
        "hibou", "hirondelle", "homard", "horloge", "igloo", "jaguar", "jardin", "jasmin",
        "jonquille", "kayak", "kiwi", "koala", "lagune", "lampion", "lanterne", "lapin",
        "lavande", "lézard", "libellule", "licorne", "lierre", "limace", "lion", "loutre",
        "luciole", "lune", "lynx", "madeleine", "magnolia", "manchot", "mandarine", "mangue",
        "marmotte", "marron", "médaille", "melon", "mésange", "miroir", "moineau", "montagne",
        "mouette", "mouton", "myrtille", "navet", "nénuphar", "noisette", "nuage", "oasis",
        "océan", "olive", "orange", "orchidée", "oreiller", "ortie", "otarie", "ours",
        "paille", "palmier", "panda", "panier", "papillon", "parapluie", "pastèque", "paon",
        "pélican", "pelle", "perle", "perroquet", "phare", "piano", "pinceau", "pingouin",
        "pirogue", "pistache", "plume", "poire", "poireau", "pomme", "potiron", "poulpe",
        "prairie", "prune", "puzzle", "radis", "raisin", "renard", "requin", "rivière",
        "rocher", "roseau", "rossignol", "rubis", "ruche", "sabot", "sablier", "salade",
        "sapin", "sardine", "saule", "saumon", "sauterelle", "sifflet", "singe", "sirop",
        "soleil", "sorbet", "source", "souris", "statue", "tambour", "tapis", "tartine",
        "taupe", "théière", "tigre", "tilleul", "toboggan", "tomate", "tortue", "toucan",
        "tournesol", "tracteur", "trèfle", "tricot", "trompette", "tulipe", "vache", "vague",
    )
}
