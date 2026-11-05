package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.requests.*;
import br.com.manager.repository.*;
import br.com.manager.utils.FinanceRules;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferRepository offers;
    private final FinanceRepository finances;
    private final FinanceService budgets;
    private final DayProgressRepository calendar;

    public TransferService(
        TransferRepository offers,
        FinanceRepository finances,
        FinanceService budgets,
        DayProgressRepository calendar
    ) {
        this.offers = offers;
        this.finances = finances;
        this.budgets = budgets;
        this.calendar = calendar;
    }

    @Transactional
    public List<Offer> list(String id, long team) {
        budgets.lock(id);
        budgets.initialize(id, team);
        if (!finances.live(id)) process(id, team, budgets.date(id, team));
        return offers.list(id, team);
    }

    @Transactional
    public List<Offer> completed(String id, long team) {
        budgets.lock(id);
        return offers.completed(id);
    }

    @Transactional
    public List<Contract> market(String id, long team, String query) {
        budgets.lock(id);
        budgets.initialize(id, team);
        if (query == null || query.strip().length() < 2 || query.length() > 80) throw new DefaultErrorException(
            HttpStatus.BAD_REQUEST,
            "Informe entre dois e oitenta caracteres para buscar jogadores."
        );
        return finances.market(id, query.strip());
    }

    private Contract target(String id, long player) {
        var c = finances.contract(id, player);
        if (c == null) throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Jogador não encontrado nesta liga.");
        return c;
    }

    @Transactional
    public Contract player(String id, long team, long player) {
        budgets.lock(id);
        budgets.initialize(id, team);
        return target(id, player);
    }

    private int[] meters(String id, long buyer, Contract c, TransferOfferRequest r, LocalDate date, boolean approved) {
        long fee = Objects.equals(c.teamId(), buyer) ? 0 : FinanceRules.askingFee(c, date);
        int club = approved || fee == 0 ? 100 : FinanceRules.meter((double) r.fee() / fee);
        long desired = FinanceRules.desiredSalary(
            c,
            finances.rating(id, buyer),
            c.teamId() == null ? finances.rating(id, buyer) : finances.rating(id, c.teamId())
        );
        double equivalent =
            r.salaryMonthly() +
            (double) r.signingBonus() / r.months() +
            Math.min(r.appearanceBonus() * 2 + r.goalBonus(), desired * .15);
        return new int[] { club, FinanceRules.meter(equivalent / desired) };
    }

    private void validateTarget(String id, long buyer, Contract c, TransferOfferRequest r) {
        if (c.teamId() == null || Objects.equals(c.teamId(), buyer)) {
            if (r.fee() != 0) fail("Jogadores livres e renovações não têm taxa de transferência.");
        } else if (finances.squadSize(id, c.teamId()) <= 16) fail(
            "O clube vendedor não pode liberar o atleta com um elenco tão reduzido."
        );
        if (
            r.salaryMonthly() <= 0 ||
            r.months() < 6 ||
            r.months() > 60 ||
            r.fee() < 0 ||
            r.signingBonus() < 0 ||
            r.appearanceBonus() < 0 ||
            r.goalBonus() < 0 ||
            r.titleBonus() < 0
        ) fail("Informe condições válidas para o contrato.");
    }

    @Transactional
    public Offer submit(String id, long buyer, TransferOfferRequest input) {
        budgets.lock(id);
        budgets.editable(id, buyer);
        budgets.requireApproved(id, buyer);
        budgets.initialize(id, buyer);
        var c = target(id, input.playerId());
        validateTarget(id, buyer, c, input);
        if (offers.duplicate(id, buyer, c.playerId())) fail(
            "Já existe uma proposta em andamento para esse jogador. Melhore ou retire a proposta existente."
        );
        budgets.requireFunds(id, buyer, input, null);
        var date = budgets.date(id, buyer);
        boolean approved = c.teamId() == null || Objects.equals(c.teamId(), buyer) || !c.humanClub();
        var meters = meters(id, buyer, c, input, date, false);
        long number = offers.create(
            id,
            buyer,
            c.teamId(),
            budgets.ensurePlan(id, buyer).seasonId(),
            input,
            date,
            approved,
            meters[0],
            meters[1]
        );
        if (approved) evaluate(id, offers.offer(id, number), date);
        budgets.changed(id, buyer);
        if (c.teamId() != null && c.teamId() != buyer) budgets.changed(id, c.teamId());
        return offers.offer(id, number);
    }

    private Offer owned(String id, long number, long team, boolean seller) {
        var offer = offers.offer(id, number);
        if (offer == null) throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Proposta não encontrada.");
        if (seller ? !Objects.equals(offer.sellerId(), team) : offer.buyerId() != team) throw new DefaultErrorException(
            HttpStatus.FORBIDDEN,
            "Esta proposta não pertence ao seu clube."
        );
        return offer;
    }

    @Transactional
    public Offer improve(String id, long buyer, long number, TransferOfferRequest input) {
        budgets.lock(id);
        budgets.editable(id, buyer);
        var old = owned(id, number, buyer, false);
        if (!Set.of("PENDING", "NEGOTIABLE", "CLUB_REVIEW").contains(old.status())) fail(
            "Esta proposta já foi encerrada."
        );
        if (!old.expiresDate().isAfter(budgets.date(id, buyer))) fail("A proposta expirou. Envie uma nova negociação.");
        if (input.playerId() != old.playerId()) fail("Não é possível trocar o atleta de uma proposta.");
        if (
            input.fee() < old.fee() ||
            input.salaryMonthly() < old.salaryMonthly() ||
            input.signingBonus() < old.signingBonus() ||
            (input.fee() == old.fee() &&
                input.salaryMonthly() == old.salaryMonthly() &&
                input.signingBonus() == old.signingBonus())
        ) fail("Melhore a taxa, o salário ou as luvas sem reduzir os valores já oferecidos.");
        var c = target(id, input.playerId());
        if (!Objects.equals(c.teamId(), old.sellerId())) fail("O jogador mudou de clube. Inicie uma nova proposta.");
        validateTarget(id, buyer, c, input);
        budgets.requireFunds(id, buyer, input, number);
        var date = budgets.date(id, buyer);
        var meter = meters(id, buyer, c, input, date, old.sellerApproved() && c.humanClub());
        offers.revise(id, number, input, date, meter[0], meter[1]);
        if (old.sellerApproved()) evaluate(id, offers.offer(id, number), date);
        budgets.changed(id, buyer);
        return offers.offer(id, number);
    }

    @Transactional
    public Offer cancel(String id, long team, long number) {
        budgets.lock(id);
        var o = owned(id, number, team, false);
        if (!Set.of("PENDING", "NEGOTIABLE", "CLUB_REVIEW").contains(o.status())) fail(
            "Esta proposta já foi encerrada."
        );
        offers.status(id, number, "CANCELLED", "O clube contratante retirou a proposta.", null);
        budgets.changed(id, team);
        return offers.offer(id, number);
    }

    @Transactional
    public Offer decideClub(String id, long seller, long number, TransferDecisionRequest input) {
        budgets.lock(id);
        budgets.editable(id, seller);
        var o = owned(id, number, seller, true);
        if (!o.status().equals("CLUB_REVIEW") || o.revision() != input.revision()) fail(
            "A proposta mudou ou já foi respondida. Atualize a lista."
        );
        if (!Boolean.TRUE.equals(input.accept())) offers.status(
            id,
            number,
            "REJECTED",
            "O clube vendedor recusou a negociação.",
            null
        );
        else {
            var c = target(id, o.playerId());
            if (!Objects.equals(c.teamId(), seller)) fail("O atleta não pertence mais ao seu clube.");
            offers.sellerApproval(id, number);
            var date = budgets.date(id, o.buyerId());
            var sellerDate = budgets.date(id, seller);
            if (sellerDate.isAfter(date)) date = sellerDate;
            evaluate(id, offers.offer(id, number), date);
        }
        budgets.changed(id, seller);
        budgets.changed(id, o.buyerId());
        return offers.offer(id, number);
    }

    private void evaluate(String id, Offer o, LocalDate date) {
        int interest = Math.min(o.clubMeter(), o.playerMeter());
        if (interest >= 92) {
            complete(id, o, date);
            return;
        }
        if (interest < 25) {
            respond(
                id,
                o,
                "REJECTED",
                "As condições ficaram muito distantes do interesse do atleta ou de seu clube.",
                date
            );
            return;
        }
        int days = 1 + new Random(Objects.hash(id, o.id(), o.revision())).nextInt(4);
        offers.status(
            id,
            o.id(),
            "PENDING",
            "O atleta e seu representante estão analisando. Resposta prevista para " + date.plusDays(days) + ".",
            date.plusDays(days)
        );
    }

    public boolean process(String id, long buyer, LocalDate date) {
        boolean changed = false;
        for (var o : offers.pending(id, buyer)) {
            if (!o.expiresDate().isAfter(date)) {
                respond(id, o, "EXPIRED", "A proposta expirou e os recursos reservados foram liberados.", date);
                changed = true;
                continue;
            }
            var c = finances.contract(id, o.playerId());
            if (c == null || !Objects.equals(c.teamId(), o.sellerId())) {
                respond(id, o, "CANCELLED", "O jogador mudou de clube durante a negociação.", date);
                changed = true;
                continue;
            }
            if (!o.status().equals("PENDING") || o.decisionDate() == null || o.decisionDate().isAfter(date)) continue;
            changed = true;
            int interest = Math.min(o.clubMeter(), o.playerMeter());
            double roll = new Random(Objects.hash(id, o.id(), o.revision(), "decision")).nextDouble();
            if ((interest >= 75 && roll < .7) || (interest >= 55 && roll < .25)) complete(id, o, date);
            else if (br.com.manager.utils.NegotiationRules.decision(interest, roll).equals("NEGOTIABLE")) respond(
                id,
                o,
                "NEGOTIABLE",
                "A proposta foi insuficiente, mas há interesse em continuar. Melhore a taxa para o clube e o salário ou as luvas para o atleta.",
                date
            );
            else respond(
                id,
                o,
                "REJECTED",
                "Após analisar por alguns dias, o atleta ou seu clube rejeitou as condições.",
                date
            );
        }
        if (changed) budgets.changed(id, buyer);
        return changed;
    }

    private void respond(String id, Offer o, String status, String feedback, LocalDate date) {
        offers.status(id, o.id(), status, feedback, null);
        calendar.event(id, o.buyerId(), date, "TRANSFER", "Negociação: " + o.playerName(), feedback);
        if (o.sellerId() != null && o.sellerId() != o.buyerId()) calendar.event(
            id,
            o.sellerId(),
            date,
            "TRANSFER",
            "Resposta de negociação: " + o.playerName(),
            feedback
        );
    }

    private void complete(String id, Offer o, LocalDate date) {
        var c = target(id, o.playerId());
        if (!Objects.equals(c.teamId(), o.sellerId())) {
            respond(id, o, "CANCELLED", "O atleta não está mais disponível neste clube.", date);
            return;
        }
        if (
            c.teamId() != null &&
            c.teamId() != o.buyerId() &&
            (finances.ready(id, c.teamId()) || finances.squadSize(id, c.teamId()) <= 16)
        ) {
            offers.status(
                id,
                o.id(),
                "PENDING",
                "O clube vendedor está preparando uma partida ou precisa ajustar seu elenco. Aguardando liberação.",
                date.plusDays(1)
            );
            return;
        }
        try {
            validateTarget(id, o.buyerId(), c, offers.terms(o));
            budgets.requireFunds(id, o.buyerId(), offers.terms(o), o.id());
        } catch (DefaultErrorException e) {
            respond(id, o, "REJECTED", "A contratação não pôde ser concluída: " + e.getMessage(), date);
            return;
        }
        long cost = o.fee() + o.signingBonus();
        finances.ledger(
            id,
            o.buyerId(),
            date,
            "TRANSFER",
            "Contratação/renovação: " + o.playerName(),
            -cost,
            "TRANSFER_BUY:" + o.id()
        );
        finances.transferSpent(id, o.buyerId(), budgets.ensurePlan(id, o.buyerId()).seasonId(), cost);
        if (c.teamId() != null && c.teamId() != o.buyerId()) {
            finances.ledger(
                id,
                c.teamId(),
                date,
                "TRANSFER_SALE",
                "Venda de " + o.playerName(),
                o.fee(),
                "TRANSFER_SELL:" + o.id()
            );
            finances.saleAllowance(id, c.teamId(), o.fee());
        }
        finances.sign(id, o.buyerId(), c, offers.terms(o), date);
        respond(
            id,
            o,
            "ACCEPTED",
            Objects.equals(c.teamId(), o.buyerId())
                ? "Renovação de contrato concluída."
                : "Proposta aceita. Transferência e contrato registrados.",
            date
        );
        if (c.teamId() != null && c.teamId() != o.buyerId()) budgets.changed(id, c.teamId());
    }

    private void fail(String text) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, text);
    }
}
