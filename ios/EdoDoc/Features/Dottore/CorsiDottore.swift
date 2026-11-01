import CoreTransferable
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct CorsiDottoreView: View {
    @Environment(AppModel.self) private var app
    @State private var nuovo = false

    var body: some View {
        Caricamento {
            let corsi: [RigaCorso] = try await app.api.get("/corsi")
            return corsi
        } contenuto: { corsi, aggiorna in
            Group {
                if corsi.isEmpty {
                    StatoVuoto(titolo: "Nessun corso", testo: "Crea il primo corso: nasce in bozza.", icona: "play.rectangle",
                               azione: ("Nuovo corso", { nuovo = true }))
                } else {
                    List(corsi) { corso in
                        NavigationLink { CorsoEditorView(id: corso.id) } label: {
                            HStack(spacing: 14) {
                                Copertina(id: corso.copertinaId, seme: corso.id)
                                    .frame(width: 64, height: 64)
                                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(corso.titolo).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                    Text("\(corso.moduli == 1 ? "1 modulo" : "\(corso.moduli) moduli") · \(corso.lezioniPubblicate) di \(corso.lezioni) \(corso.lezioni == 1 ? "lezione pubblicata" : "lezioni pubblicate")")
                                        .font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                                    ChipStato(testo: corso.pubblicato ? "Pubblicato" : "Bozza",
                                              colore: corso.pubblicato ? Tema.positivo : Tema.inkSecondario)
                                }
                            }
                        }
                        .rigaEdo()
                    }
                    .listaEdo()
                    .refreshable { await aggiorna() }
                }
            }
            .sheet(isPresented: $nuovo, onDismiss: { Task { await aggiorna() } }) {
                TitoloFormView(titoloFoglio: "Nuovo corso", titolo: "", descrizione: "") { corpo in
                    let _: StrutturaCorso = try await app.api.post("/corsi", corpo)
                }
            }
        }
        .telaEdo()
        .navigationTitle("Corsi")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nuovo = true } label: { Image(systemName: "plus") }.accessibilityLabel("Nuovo corso")
            }
        }
    }
}

struct TitoloFormView: View {
    let titoloFoglio: String
    @State var titolo: String
    @State var descrizione: String
    let salva: (SalvaTitolo) async throws -> Void

    var body: some View {
        ModuloFoglio(titolo: titoloFoglio, valido: titolo.nonVuoto != nil) {
            try await salva(SalvaTitolo(titolo: titolo, descrizione: descrizione.nonVuoto))
        } contenuto: {
            Section {
                TextField("Titolo", text: $titolo)
                TextField("Descrizione", text: $descrizione, axis: .vertical).lineLimit(3...8)
            }
        }
    }
}

/// Riduce la foto scelta perché stia nel limite di 5 MB del backend.
enum Foto {
    static func jpeg(_ dati: Data, latoMassimo: CGFloat = 1800) -> Data? {
        guard let immagine = UIImage(data: dati) else { return nil }
        let scala = min(1, latoMassimo / max(immagine.size.width, immagine.size.height))
        let dimensione = CGSize(width: immagine.size.width * scala, height: immagine.size.height * scala)
        let formato = UIGraphicsImageRendererFormat()
        formato.scale = 1
        let ridotta = UIGraphicsImageRenderer(size: dimensione, format: formato).image { _ in
            immagine.draw(in: CGRect(origin: .zero, size: dimensione))
        }
        return ridotta.jpegData(compressionQuality: 0.82)
    }
}

struct CorsoEditorView: View {
    let id: Int
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @State private var modifica = false
    @State private var nuovoModulo = false
    @State private var foto: PhotosPickerItem?
    @State private var elimina = false

    var body: some View {
        Caricamento {
            let struttura: StrutturaCorso = try await app.api.get("/corsi/\(id)")
            return struttura
        } contenuto: { s, aggiorna in
            List {
                VStack(alignment: .leading, spacing: 14) {
                    Copertina(id: s.corso.copertinaId, seme: s.corso.id)
                        .frame(height: 190)
                        .clipShape(RoundedRectangle(cornerRadius: Tema.raggioPannello, style: .continuous))
                    Text(s.corso.titolo).font(Tipografia.display(32)).foregroundStyle(Tema.ink)
                    if let descrizione = s.corso.descrizione {
                        Text(descrizione).font(Tipografia.body).foregroundStyle(Tema.inkSecondario)
                    }
                    Toggle("Pubblicato", isOn: Binding(get: { s.corso.pubblicato }, set: { valore in
                        Task {
                            await app.esegui {
                                let _: StrutturaCorso = try await app.api.post("/corsi/\(id)/pubblicazione", [URLQueryItem(name: "pubblicato", value: "\(valore)")])
                            }
                            await aggiorna()
                        }
                    }))
                    .font(Tipografia.bodyMedio)
                    .foregroundStyle(Tema.ink)
                }
                .rigaEdo()
                .listRowSeparator(.hidden)

                ForEach(Array(s.moduli.enumerated()), id: \.element.id) { indice, voce in
                    SezioneModulo(voce: voce, indice: indice, totale: s.moduli, corsoId: id, aggiorna: aggiorna)
                }
                Button("Aggiungi un modulo") { nuovoModulo = true }
                    .font(Tipografia.bodyMedio)
                    .rigaEdo()
                    .listRowSeparator(.hidden)
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(isPresented: $modifica, onDismiss: { Task { await aggiorna() } }) {
                TitoloFormView(titoloFoglio: "Modifica corso", titolo: s.corso.titolo, descrizione: s.corso.descrizione.testo) { corpo in
                    let _: StrutturaCorso = try await app.api.put("/corsi/\(id)", corpo)
                }
            }
            .sheet(isPresented: $nuovoModulo, onDismiss: { Task { await aggiorna() } }) {
                TitoloFormView(titoloFoglio: "Nuovo modulo", titolo: "", descrizione: "") { corpo in
                    let _: RigaModulo = try await app.api.post("/corsi/\(id)/moduli", corpo)
                }
            }
            .onChange(of: foto) { _, scelta in
                guard let scelta else { return }
                Task {
                    await carica(scelta)
                    foto = nil
                    await aggiorna()
                }
            }
        }
        .telaEdo()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Modifica", systemImage: "pencil") { modifica = true }
                    Button("Elimina il corso", systemImage: "trash", role: .destructive) { elimina = true }
                } label: {
                    Image(systemName: "ellipsis")
                }
                .accessibilityLabel("Azioni")
            }
            ToolbarItem(placement: .primaryAction) {
                PhotosPicker(selection: $foto, matching: .images) {
                    Image(systemName: "photo")
                }
                .accessibilityLabel("Cambia copertina")
            }
        }
        .confirmationDialog("Eliminare il corso?", isPresented: $elimina, titleVisibility: .visible) {
            Button("Elimina", role: .destructive) {
                Task {
                    let riuscito = await app.esegui({ try await app.api.delete("/corsi/\(id)") }, successo: "Corso eliminato")
                    if riuscito { chiudi() }
                }
            }
        }
    }

    private func carica(_ scelta: PhotosPickerItem) async {
        await app.esegui({
            guard let dati = try await scelta.loadTransferable(type: Data.self), let jpeg = Foto.jpeg(dati) else {
                throw ErroreAPI.server(stato: 400, messaggio: "Immagine non valida.")
            }
            let _: Risposta<Vuoto> = try await app.api.multipart("/corsi/\(id)/copertina", campi: [:],
                file: ParteFile(campo: "file", nomeFile: "copertina.jpg", tipo: "image/jpeg", dati: jpeg))
        }, successo: "Copertina caricata")
    }
}

private struct SezioneModulo: View {
    let voce: ModuloConLezioni
    let indice: Int
    let totale: [ModuloConLezioni]
    let corsoId: Int
    let aggiorna: () async -> Void
    @Environment(AppModel.self) private var app
    @State private var modifica = false
    @State private var nuovaLezione = false

    var body: some View {
        Section {
            ForEach(Array(voce.lezioni.enumerated()), id: \.element.id) { posizione, lezione in
                NavigationLink {
                    LezioneEditorView(lezione: lezione, moduloId: voce.modulo.id)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: lezione.tipo == .video ? "play.fill" : "text.alignleft")
                            .font(.system(size: 12))
                            .foregroundStyle(Tema.ember)
                        Text(lezione.titolo).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                        Spacer()
                        if !lezione.pubblicata {
                            ChipStato(testo: "Bozza")
                        } else if !lezione.completa {
                            ChipStato(testo: "Incompleta", colore: Tema.attenzione)
                        }
                    }
                }
                .rigaEdo()
                .swipeActions(edge: .leading) {
                    if posizione > 0 {
                        Button("Su") { Task { await sposta(lezione: posizione, di: -1) } }.tint(Tema.brand)
                    }
                    if posizione < voce.lezioni.count - 1 {
                        Button("Giù") { Task { await sposta(lezione: posizione, di: 1) } }.tint(Tema.brand)
                    }
                }
            }
            Button("Aggiungi una lezione") { nuovaLezione = true }
                .font(Tipografia.label)
                .rigaEdo()
                .sheet(isPresented: $nuovaLezione, onDismiss: { Task { await aggiorna() } }) {
                    LezioneFormView(lezione: nil, moduloId: voce.modulo.id)
                }
        } header: {
            HStack {
                Overline(voce.modulo.titolo)
                Spacer()
                Menu {
                    Button("Modifica", systemImage: "pencil") { modifica = true }
                    if indice > 0 {
                        Button("Sposta su", systemImage: "arrow.up") { Task { await sposta(modulo: -1) } }
                    }
                    if indice < totale.count - 1 {
                        Button("Sposta giù", systemImage: "arrow.down") { Task { await sposta(modulo: 1) } }
                    }
                    Button("Elimina", systemImage: "trash", role: .destructive) {
                        Task {
                            await app.esegui({ try await app.api.delete("/moduli/\(voce.modulo.id)") }, successo: "Modulo eliminato")
                            await aggiorna()
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis").frame(width: 44, height: 32)
                }
                .accessibilityLabel("Azioni del modulo")
            }
            .sheet(isPresented: $modifica, onDismiss: { Task { await aggiorna() } }) {
                TitoloFormView(titoloFoglio: "Modifica modulo", titolo: voce.modulo.titolo, descrizione: voce.modulo.descrizione.testo) { corpo in
                    let _: RigaModulo = try await app.api.put("/moduli/\(voce.modulo.id)", corpo)
                }
            }
        }
    }

    private func sposta(modulo delta: Int) async {
        var ids = totale.map(\.modulo.id)
        ids.swapAt(indice, indice + delta)
        await app.esegui {
            let _: Vuoto = try await app.api.put("/corsi/\(corsoId)/moduli/ordine", Ordine(ids: ids))
        }
        await aggiorna()
    }

    private func sposta(lezione posizione: Int, di delta: Int) async {
        var ids = voce.lezioni.map(\.id)
        ids.swapAt(posizione, posizione + delta)
        await app.esegui {
            let _: Vuoto = try await app.api.put("/moduli/\(voce.modulo.id)/lezioni/ordine", Ordine(ids: ids))
        }
        await aggiorna()
    }
}

struct LezioneFormView: View {
    let lezione: RigaLezione?
    let moduloId: Int
    var salvata: (RigaLezione) -> Void = { _ in }
    @Environment(AppModel.self) private var app
    @State private var tipo = TipoLezione.testo
    @State private var titolo = ""
    @State private var descrizione = ""
    @State private var contenuto = ""

    var body: some View {
        ModuloFoglio(titolo: lezione == nil ? "Nuova lezione" : "Modifica lezione", valido: titolo.nonVuoto != nil) {
            let corpo = SalvaLezione(tipo: tipo, titolo: titolo, descrizione: descrizione.nonVuoto, contenuto: contenuto.nonVuoto)
            if let lezione {
                salvata(try await app.api.put("/lezioni/\(lezione.id)", corpo))
            } else {
                salvata(try await app.api.post("/moduli/\(moduloId)/lezioni", corpo))
            }
        } contenuto: {
            Section {
                if lezione == nil {
                    Picker("Tipo", selection: $tipo) {
                        ForEach(TipoLezione.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                TextField("Titolo", text: $titolo)
                TextField("Descrizione", text: $descrizione, axis: .vertical)
            }
            if tipo == .testo {
                Section("Testo in Markdown") {
                    TextField("Scrivi la lezione", text: $contenuto, axis: .vertical).lineLimit(8...30)
                }
            }
        }
        .onAppear {
            guard let lezione else { return }
            tipo = lezione.tipo
            titolo = lezione.titolo
            descrizione = lezione.descrizione.testo
            contenuto = lezione.contenuto.testo
        }
    }
}

struct LezioneEditorView: View {
    @State var lezione: RigaLezione
    let moduloId: Int
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @State private var modifica = false
    @State private var video: PhotosPickerItem?
    @State private var caricamento: Double?
    @State private var stato: StatoVideo?
    @State private var riproduzione: URL?
    @State private var elimina = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Overline(lezione.tipo.etichetta)
                Text(lezione.titolo).font(Tipografia.display(34)).foregroundStyle(Tema.ink)
                if let descrizione = lezione.descrizione {
                    Text(descrizione).font(Tipografia.body).foregroundStyle(Tema.inkSecondario)
                }
                Toggle("Pubblicata", isOn: Binding(get: { lezione.pubblicata }, set: { valore in
                    Task {
                        await app.esegui {
                            lezione = try await app.api.post("/lezioni/\(lezione.id)/pubblicazione", [URLQueryItem(name: "pubblicata", value: "\(valore)")])
                        }
                    }
                }))
                .font(Tipografia.bodyMedio)
                .foregroundStyle(Tema.ink)
                if !lezione.completa {
                    Text(lezione.tipo == .video ? "Manca il video pronto: il paziente non la vedrà." : "Manca il testo: il paziente non la vedrà.")
                        .font(Tipografia.nota)
                        .foregroundStyle(Tema.attenzione)
                }
                if lezione.tipo == .video { sezioneVideo }
                if lezione.tipo == .testo, let contenuto = lezione.contenuto {
                    TestoMarkdown(sorgente: contenuto)
                }
            }
            .padding(Tema.margine)
        }
        .telaEdo()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Modifica", systemImage: "pencil") { modifica = true }
                    Button("Elimina", systemImage: "trash", role: .destructive) { elimina = true }
                } label: {
                    Image(systemName: "ellipsis")
                }
                .accessibilityLabel("Azioni")
            }
        }
        .sheet(isPresented: $modifica) {
            LezioneFormView(lezione: lezione, moduloId: moduloId) { lezione = $0 }
        }
        .confirmationDialog("Eliminare la lezione?", isPresented: $elimina, titleVisibility: .visible) {
            Button("Elimina", role: .destructive) {
                Task {
                    let riuscito = await app.esegui({ try await app.api.delete("/lezioni/\(lezione.id)") }, successo: "Lezione eliminata")
                    if riuscito { chiudi() }
                }
            }
        }
        .onChange(of: video) { _, scelta in
            guard let scelta else { return }
            Task {
                await carica(scelta)
                video = nil
            }
        }
    }

    @ViewBuilder
    private var sezioneVideo: some View {
        VStack(alignment: .leading, spacing: 12) {
            RigaDato(etichetta: "Stato del video", valore: (stato?.stato ?? lezione.videoStato)?.etichetta ?? "Nessun video")
            RigaDato(etichetta: "Errore", valore: lezione.videoErrore)
            if let caricamento {
                ProgressView(value: caricamento) { Text("Caricamento \(Int(caricamento * 100))%").font(Tipografia.nota) }
            } else {
                PhotosPicker(selection: $video, matching: .videos) {
                    Text(lezione.videoStato == nil ? "Scegli il video" : "Sostituisci il video")
                        .font(Tipografia.bodyMedio.weight(.semibold))
                        .foregroundStyle(Tema.suEmber)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(Tema.ember, in: Capsule())
                }
            }
            if lezione.videoStato == .elaborazione || lezione.videoStato == .caricamento {
                Button("Aggiorna lo stato") { Task { await sincronizza() } }
                    .buttonStyle(BottoneVetro())
            }
            if lezione.videoStato == .pronto {
                if let riproduzione {
                    LettoreVideo(url: riproduzione)
                        .aspectRatio(16 / 9, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: Tema.raggioControllo, style: .continuous))
                } else {
                    Button("Guarda il video") {
                        Task {
                            await app.esegui {
                                let r: Riproduzione = try await app.api.get("/lezioni/\(lezione.id)/video/riproduzione")
                                riproduzione = r.hlsUrl.flatMap(URL.init(string:))
                            }
                        }
                    }
                    .buttonStyle(BottoneVetro())
                }
            }
        }
    }

    private func sincronizza() async {
        await app.esegui {
            let nuovo: StatoVideo = try await app.api.post("/lezioni/\(lezione.id)/video/sincronizza")
            stato = nuovo
            if let aggiornata = nuovo.lezione { lezione = aggiornata }
        }
    }

    private func carica(_ scelta: PhotosPickerItem) async {
        caricamento = 0
        defer { caricamento = nil }
        await app.esegui({
            guard let file = try await scelta.loadTransferable(type: FileVideo.self) else {
                throw ErroreAPI.server(stato: 400, messaggio: "Video non leggibile.")
            }
            defer { try? FileManager.default.removeItem(at: file.url) }
            let dimensione = (try FileManager.default.attributesOfItem(atPath: file.url.path)[.size] as? NSNumber)?.int64Value ?? 0
            struct Avvia: Encodable { let dimensioneBytes: Int64 }
            let upload: UploadVideo = try await app.api.post("/lezioni/\(lezione.id)/video/upload", Avvia(dimensioneBytes: dimensione))
            guard let destinazione = URL(string: upload.uploadUrl) else { throw ErroreAPI.risposta }
            try await CaricatoreTus.carica(file: file.url, dimensione: dimensione, url: destinazione) { inviati in
                await MainActor.run { caricamento = Double(inviati) / Double(max(dimensione, 1)) }
            }
            await sincronizza()
        }, successo: "Video caricato. Cloudflare lo sta elaborando.")
    }
}

struct FileVideo: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { SentTransferredFile($0.url) } importing: { ricevuto in
            let copia = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).\(ricevuto.file.pathExtension)")
            try FileManager.default.copyItem(at: ricevuto.file, to: copia)
            return FileVideo(url: copia)
        }
    }
}

/// Upload tus diretto a Cloudflare, a blocchi e con ripresa dopo un errore di rete: stesso schema del gestionale web.
enum CaricatoreTus {
    /// Multiplo di 256 KiB, come chiede Cloudflare.
    private static let blocco = 200 * 256 * 1024
    private static let tentativi = 4

    static func carica(file: URL, dimensione: Int64, url: URL, avanzamento: @Sendable (Int64) async -> Void) async throws {
        let lettore = try FileHandle(forReadingFrom: file)
        defer { try? lettore.close() }
        var offset: Int64 = 0
        var errori = 0
        while offset < dimensione {
            try Task.checkCancellation()
            try lettore.seek(toOffset: UInt64(offset))
            guard let dati = try lettore.read(upToCount: blocco) else { break }
            var richiesta = URLRequest(url: url)
            richiesta.httpMethod = "PATCH"
            richiesta.setValue("1.0.0", forHTTPHeaderField: "Tus-Resumable")
            richiesta.setValue("\(offset)", forHTTPHeaderField: "Upload-Offset")
            richiesta.setValue("application/offset+octet-stream", forHTTPHeaderField: "Content-Type")
            do {
                let (_, risposta) = try await URLSession.shared.upload(for: richiesta, from: dati)
                guard let http = risposta as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                      let nuovo = http.value(forHTTPHeaderField: "Upload-Offset").flatMap({ Int64($0) }) else {
                    throw URLError(.badServerResponse)
                }
                offset = nuovo
                errori = 0
                await avanzamento(offset)
            } catch {
                errori += 1
                guard errori < tentativi else {
                    throw ErroreAPI.server(stato: 0, messaggio: "Caricamento interrotto. Riprova.")
                }
                try await Task.sleep(for: .seconds(errori * 2))
                offset = await offsetServer(url: url) ?? offset
            }
        }
    }

    private static func offsetServer(url: URL) async -> Int64? {
        var richiesta = URLRequest(url: url)
        richiesta.httpMethod = "HEAD"
        richiesta.setValue("1.0.0", forHTTPHeaderField: "Tus-Resumable")
        guard let (_, risposta) = try? await URLSession.shared.data(for: richiesta),
              let http = risposta as? HTTPURLResponse else { return nil }
        return http.value(forHTTPHeaderField: "Upload-Offset").flatMap { Int64($0) }
    }
}
