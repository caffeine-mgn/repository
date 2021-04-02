package pw.binom.css

val style = CSS {
    ".button" {
        display = "none"
        ":none" > {
            this.filter = "none"
        }

        "selected" {
            filter = "balck"
        }
    }
}