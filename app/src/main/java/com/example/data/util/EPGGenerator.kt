package com.example.data.util

data class Program(
    val title: String,
    val progress: Float
)

object EPGGenerator {
    fun generateEPGForChannel(channelTitle: String): List<Program> {
        val hash = channelTitle.hashCode()
        
        val prog1Title = when (Math.abs(hash) % 4) {
            0 -> "Jornal de Notícias"
            1 -> "Sessão de Cinema"
            2 -> "Grande Jogo Ao Vivo"
            else -> "Documentário Especial"
        }
        
        val prog2Title = when (Math.abs(hash + 1) % 4) {
            0 -> "Filme da Tarde"
            1 -> "Série do Momento"
            2 -> "Esporte Espetacular"
            else -> "Talk Show de Variedades"
        }
        
        val prog3Title = when (Math.abs(hash + 2) % 4) {
            0 -> "Clássico da TV"
            1 -> "Mundo Selvagem"
            2 -> "Resumo de Notícias"
            else -> "Sessão de Comédia"
        }

        // Generate progress deterministically based on channel title hash
        val progress = 0.1f + (Math.abs(hash % 100) / 100f) * 0.8f

        return listOf(
            Program(prog1Title, 1.0f),
            Program(prog2Title, progress),
            Program(prog3Title, 0.0f)
        )
    }
}
