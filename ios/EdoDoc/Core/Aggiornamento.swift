import Foundation

struct Aggiornamento: Equatable {
    let versione: String
    let url: URL
}

/// Decide l'App Store: ogni versione pubblicata diventa obbligatoria da sola.
struct ControlloAggiornamento {
    private struct Ricerca: Decodable {
        struct Scheda: Decodable {
            let version: String
            let trackViewUrl: URL
            let minimumOsVersion: String?
        }
        let results: [Scheda]
    }

    var trasporto: Trasporto = TrasportoURLSession()

    /// nil anche se l'App Store non risponde: un paziente non si blocca per un controllo fallito.
    func cerca(bundle: String, installata: String, sistema: String) async -> Aggiornamento? {
        var componenti = URLComponents(string: "https://itunes.apple.com/lookup")!
        componenti.queryItems = [URLQueryItem(name: "bundleId", value: bundle), URLQueryItem(name: "country", value: "it")]
        let richiesta = URLRequest(url: componenti.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        guard let (dati, http) = try? await trasporto.invia(richiesta), http.statusCode == 200,
              let scheda = (try? JSONDecoder().decode(Ricerca.self, from: dati))?.results.first,
              Self.successiva(scheda.version, a: installata) else { return nil }
        // Un telefono troppo vecchio per la versione nuova non potrebbe installarla e resterebbe chiuso fuori
        if let minimo = scheda.minimumOsVersion, Self.successiva(minimo, a: sistema) { return nil }
        return Aggiornamento(versione: scheda.version, url: scheda.trackViewUrl)
    }

    /// "1.0" e "1.0.0" sono la stessa versione, e 1.10 viene dopo 1.9.
    static func successiva(_ versione: String, a altra: String) -> Bool {
        let a = numeri(versione), b = numeri(altra)
        for indice in 0..<max(a.count, b.count) {
            let x = indice < a.count ? a[indice] : 0
            let y = indice < b.count ? b[indice] : 0
            if x != y { return x > y }
        }
        return false
    }

    private static func numeri(_ versione: String) -> [Int] {
        versione.split(separator: ".").map { Int($0) ?? 0 }
    }
}
