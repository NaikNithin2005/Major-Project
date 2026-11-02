'use client';

import React from 'react';
import { ThreatEvent } from '@/types';
import {
  X,
  ShieldAlert,
  ShieldCheck,
  AlertTriangle,
  ExternalLink,
  Trash2,
  Clock,
  QrCode,
  Smartphone,
  Copy,
  Check,
  Table,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  FileText,
  UserCheck,
  HelpCircle,
  Flag,
} from 'lucide-react';

interface ThreatDetailPanelProps {
  threat: ThreatEvent | null;
  isOpen: boolean;
  onClose: () => void;
  onAction?: (threatId: string, action: 'BLOCK' | 'MARK_SAFE') => Promise<void>;
  onDelete?: (threatId: string) => Promise<void>;
  isActionInProgress?: boolean;
}

interface EvaluationRow {
  dimension: string;
  status: string;
  description: string;
  isFavorable: boolean;
}

export function ThreatDetailPanel({
  threat,
  isOpen,
  onClose,
  onAction,
  onDelete,
  isActionInProgress = false,
}: ThreatDetailPanelProps) {
  const [copied, setCopied] = React.useState(false);
  const [showProceedConfirm, setShowProceedConfirm] = React.useState(false);
  const [showMatrix, setShowMatrix] = React.useState(true);
  const [showFeedbackModal, setShowFeedbackModal] = React.useState<'SAFE' | 'DANGEROUS' | null>(null);
  const [selectedFeedbackReason, setSelectedFeedbackReason] = React.useState<string>('');

  if (!isOpen || !threat) return null;

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const rawContent = threat.raw_content || threat.sanitized_preview || threat.decoded_url || '';
  const sender = threat.sender || 'Unknown Sender';
  const isQr = threat.event_type === 'QR';

  // Category Detection
  const detectCategory = (): string => {
    if (isQr) return 'QR Code Payload';
    const lower = rawContent.toLowerCase();
    if (lower.includes('otp') && (lower.includes('code') || lower.includes('verification'))) {
      return 'One-Time Password (OTP)';
    }
    if (lower.includes('delivery') || lower.includes('courier') || lower.includes('package') || lower.includes('parcel') || lower.includes('shipment')) {
      return 'Delivery & Shipping Notice';
    }
    if (lower.includes('tax') || lower.includes('challan') || lower.includes('court') || lower.includes('police') || lower.includes('arrest') || lower.includes('government')) {
      return 'Government / Tax Notice';
    }
    if (lower.includes('job') || lower.includes('hiring') || lower.includes('salary') || lower.includes('income') || lower.includes('part time')) {
      return 'Job Offer / Recruitment';
    }
    if (lower.includes('bank') || lower.includes('debited') || lower.includes('credited') || lower.includes('account') || lower.includes('inr') || lower.includes('upi')) {
      return 'Bank Transaction / Notification';
    }
    return 'General Text Message';
  };

  const category = detectCategory();

  // Verdict calculation
  const isDangerous = threat.risk_score >= 70 || threat.threat_category === 'HIGH';
  const isAttention = !isDangerous && (threat.risk_score >= 40 || threat.threat_category === 'MEDIUM');
  const isSafe = !isDangerous && !isAttention;

  const verdictLabel = isDangerous ? 'Dangerous' : isAttention ? 'Needs Attention' : 'Looks Safe';
  const verdictColor = isDangerous
    ? 'text-rose-400 bg-rose-500/10 border-rose-500/30'
    : isAttention
    ? 'text-amber-400 bg-amber-500/10 border-amber-500/30'
    : 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30';

  // Signals Detection
  const lower = rawContent.toLowerCase();
  const hasUrgency = /\b(immediately|urgent|urgently|expire|blocked today|suspended|act now|penalty)\b/i.test(lower);
  const isProtective = lower.includes('do not share') || lower.includes('never share');
  const hasCredentialHarvest = !isProtective && /\b(password|pin|cvv|otp|login to verify|update kyc|verify account)\b/i.test(lower);
  const hasPaymentDemand = /\b(send money|transfer inr|pay fee|deposit|recharge wallet|unfreeze)\b/i.test(lower);
  const hasLink = Boolean(threat.decoded_url) || lower.includes('http://') || lower.includes('https://') || lower.includes('bit.ly') || lower.includes('.xyz');
  const hasSuspiciousLink = hasLink && (lower.includes('.xyz') || lower.includes('bit.ly') || lower.includes('tinyurl') || lower.includes('.top') || lower.includes('.apk'));

  // Detailed Evaluation Matrix Rows
  const evaluationRows: EvaluationRow[] = [
    {
      dimension: 'Sender Verification',
      status: /^[A-Z]{2}-[A-Z]{2,6}$/.test(sender) ? 'Official Enterprise Header' : sender.startsWith('+') && (category.includes('Bank') || category.includes('Government')) ? 'Suspicious Personal Number' : 'Standard Sender',
      description: /^[A-Z]{2}-[A-Z]{2,6}$/.test(sender) ? 'Matches registered enterprise telecom headers.' : 'Standard mobile or service identifier.',
      isFavorable: !sender.startsWith('+') || (!category.includes('Bank') && !category.includes('Government')),
    },
    {
      dimension: 'Language Urgency',
      status: hasUrgency ? 'Artificial Urgency Detected' : 'Normal Calm Tone',
      description: hasUrgency ? 'Uses pressure phrases to force hasty user action.' : 'No psychological fear tactics detected.',
      isFavorable: !hasUrgency,
    },
    {
      dimension: 'Link Safety',
      status: !hasLink ? 'No Links Present' : hasSuspiciousLink ? 'Suspicious or Masked Link' : 'External Link Present',
      description: !hasLink ? 'Message contains no external website redirects.' : hasSuspiciousLink ? 'Contains an unverified external or shortened domain.' : 'External web address. Verify before opening.',
      isFavorable: !hasSuspiciousLink,
    },
    {
      dimension: 'Personal Info Request',
      status: hasCredentialHarvest ? 'Demands Sensitive Credentials' : isProtective ? 'Protective Reminder Included' : 'No Sensitive Requests',
      description: hasCredentialHarvest ? 'Requests passwords, PIN, or OTPs. Official entities never ask this.' : 'Does not demand secret passwords or login tokens.',
      isFavorable: !hasCredentialHarvest,
    },
    {
      dimension: 'Financial Action Required',
      status: hasPaymentDemand ? 'Immediate Payment Demanded' : category.includes('Bank') ? 'Informational Receipt' : 'None Required',
      description: hasPaymentDemand ? 'Requests upfront fees, payments, or money transfers.' : 'No unauthorized monetary transactions requested.',
      isFavorable: !hasPaymentDemand,
    },
    {
      dimension: 'Overall Assessment',
      status: verdictLabel,
      description: isSafe ? 'Content passed all safety checks. No malicious indicators found.' : isDangerous ? 'High scam risk detected. Do not click links or share information.' : 'Exercise caution and verify the sender before taking action.',
      isFavorable: isSafe,
    },
  ];

  // Why Flagged Explanation
  const flaggedReasons: string[] = [];
  if (hasUrgency) flaggedReasons.push('creates artificial urgency by claiming an account is suspended or deadlines are expiring');
  if (hasCredentialHarvest) flaggedReasons.push('requests confidential information such as passwords, OTPs, or card details');
  if (hasSuspiciousLink) flaggedReasons.push('contains an unverified or shortened link not matching the claimed organization');
  if (hasPaymentDemand) flaggedReasons.push('demands an upfront money transfer or payment under false pretenses');

  const whyFlaggedText = isSafe
    ? 'This message was analyzed using security intelligence and verified safe. It contains no deceptive urgency, credential harvesting attempts, or suspicious web links.'
    : flaggedReasons.length > 0
    ? `This message was flagged because it ${flaggedReasons.join(', and ')}. Legitimate organizations do not request sensitive actions through unsolicited messages.`
    : threat.explanation || (threat.reasons && threat.reasons.join('; ')) || 'This message exhibited characteristics frequently associated with social engineering and scam campaigns.';

  // Contextual Recommendations
  const getRecommendations = (): string[] => {
    if (category.includes('Bank') || category.includes('OTP')) {
      return [
        'Never share your one-time password (OTP) or PIN with anyone, even if they claim to represent your bank.',
        'If you are concerned about your account, open your official mobile banking app directly or visit your local branch.',
        'Call your bank using the phone number printed on the back of your debit card, never the number in this text.',
      ];
    }
    if (category.includes('Delivery')) {
      return [
        'Check your recent delivery orders inside your official merchant or carrier app.',
        'Legitimate courier companies never ask for unexpected fees or redelivery payments via text message.',
        'Do not open tracking links in text messages unless you were specifically expecting a shipment from this courier.',
      ];
    }
    if (category.includes('Government')) {
      return [
        'Government agencies communicate official matters through postal mail or verified online portals.',
        'Never pay traffic fines, taxes, or court penalties through links received in text messages.',
        'Verify this notice by visiting the official government department website independently.',
      ];
    }
    if (category.includes('Job')) {
      return [
        'Legitimate employers never offer high-paying jobs through unsolicited text messages.',
        'Real companies never demand registration fees or equipment advance payments before employment.',
        'Research the hiring company independently on official career websites before responding.',
      ];
    }
    if (isDangerous) {
      return [
        'Do not click any web links or open attachments in this message.',
        'Do not reply to this text or call back any phone numbers mentioned in it.',
        'Delete this message or keep it safely quarantined in Squish.',
      ];
    }
    return [
      'Squish found no security threats or scam indicators in this content.',
      'You can interact with this message normally. Standard caution is always recommended with unfamiliar contacts.',
    ];
  };

  const recommendations = getRecommendations();

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-slate-950/80 backdrop-blur-sm flex justify-end transition-opacity animate-fade-in-up">
      <div
        className="w-full max-w-xl bg-slate-900 border-l border-slate-800 shadow-2xl flex flex-col h-full overflow-hidden animate-slide-in-right"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-900/95 sticky top-0 z-10">
          <div className="flex items-center space-x-3">
            <div className={`w-9 h-9 rounded-lg flex items-center justify-center border ${verdictColor}`}>
              {isDangerous ? (
                <ShieldAlert className="w-5 h-5 text-rose-400" />
              ) : isSafe ? (
                <ShieldCheck className="w-5 h-5 text-emerald-400" />
              ) : (
                <AlertTriangle className="w-5 h-5 text-amber-400" />
              )}
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
                Safety Analysis Report
                <span className="text-xs font-mono text-slate-400 font-normal">
                  #{threat.id.slice(0, 8)}
                </span>
              </h2>
              <p className="text-xs text-slate-400 flex items-center gap-1.5 mt-0.5">
                <Clock className="w-3.5 h-3.5" />
                <span>Checked on {new Date(threat.timestamp).toLocaleString()}</span>
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition-colors"
            aria-label="Close details"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-5">
          {/* Section B: Detection Result Card */}
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-4 flex items-center justify-between">
            <div>
              <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block mb-1">
                Detection Verdict
              </span>
              <div className="flex items-center gap-2">
                <span
                  className={`px-3 py-1 rounded-md text-xs font-black uppercase tracking-wider border ${verdictColor}`}
                >
                  {verdictLabel}
                </span>
                <span className="text-xs font-semibold text-cyan-400">
                  {category}
                </span>
              </div>
            </div>
            <div className="text-right">
              <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block mb-1">
                Safety Score
              </span>
              <span className="text-xl font-black font-mono text-slate-100">
                {threat.risk_score}
                <span className="text-xs text-slate-500 font-normal">/100</span>
              </span>
            </div>
          </div>

          {/* Section A: Message Information */}
          <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-4 space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-slate-800/80">
              <div className="flex items-center gap-2">
                {isQr ? <QrCode className="w-4 h-4 text-cyan-400" /> : <Smartphone className="w-4 h-4 text-cyan-400" />}
                <span className="text-xs font-bold text-slate-200">
                  Sender: <span className="font-semibold text-white">{sender}</span>
                </span>
              </div>
              <button
                onClick={() => handleCopy(rawContent)}
                className="inline-flex items-center gap-1 text-[11px] text-cyan-400 hover:text-cyan-300 font-medium"
              >
                {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'Copied' : 'Copy Text'}</span>
              </button>
            </div>

            <div className="bg-slate-900/90 border border-slate-800/90 rounded-lg p-3">
              <p className="whitespace-pre-wrap text-xs text-slate-200 leading-relaxed font-sans select-all">
                {rawContent || 'No message content retained.'}
              </p>
            </div>

            {threat.decoded_url && (
              <div className="pt-2 border-t border-slate-800/60 flex items-center justify-between">
                <div>
                  <span className="text-[10px] uppercase font-bold text-cyan-400 block">
                    Detected Website Link:
                  </span>
                  <span className="text-xs font-mono text-slate-300 break-all">{threat.decoded_url}</span>
                </div>
              </div>
            )}
          </div>

          {/* Section C: Why Was This Message Flagged? */}
          <div className="bg-slate-800/40 border border-slate-700/60 rounded-xl p-4 space-y-2">
            <div className="flex items-center gap-2">
              <HelpCircle className="w-4 h-4 text-cyan-400" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                Why Was This Message Flagged?
              </h3>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed bg-slate-900/60 p-3 rounded-lg border border-slate-800">
              {whyFlaggedText}
            </p>
          </div>

          {/* Section D: Detailed Breakdown Table / Evaluation Matrix */}
          <div className="bg-slate-800/40 border border-slate-700/60 rounded-xl p-4 space-y-3">
            <div
              className="flex items-center justify-between cursor-pointer"
              onClick={() => setShowMatrix(!showMatrix)}
            >
              <div className="flex items-center gap-2">
                <Table className="w-4 h-4 text-cyan-400" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                  Detailed Evaluation Matrix
                </h3>
              </div>
              <button className="text-slate-400 hover:text-slate-200">
                {showMatrix ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
              </button>
            </div>

            {showMatrix && (
              <div className="space-y-2 pt-1">
                {evaluationRows.map((row, idx) => (
                  <div
                    key={idx}
                    className="p-2.5 rounded-lg bg-slate-900/70 border border-slate-800 flex items-start justify-between gap-3"
                  >
                    <div className="space-y-0.5">
                      <span className="text-xs font-semibold text-slate-200 block">
                        {row.dimension}
                      </span>
                      <p className="text-[11px] text-slate-400 leading-normal">
                        {row.description}
                      </p>
                    </div>
                    <span
                      className={`shrink-0 px-2 py-0.5 rounded text-[10px] font-bold border ${
                        row.isFavorable
                          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                          : 'bg-rose-500/10 text-rose-400 border-rose-500/30'
                      }`}
                    >
                      {row.status}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Section E: Contextual Recommendations */}
          <div className="bg-slate-800/40 border border-slate-700/60 rounded-xl p-4 space-y-2.5">
            <div className="flex items-center gap-2">
              <UserCheck className="w-4 h-4 text-emerald-400" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                Contextual Safety Guidance
              </h3>
            </div>
            <ul className="space-y-2">
              {recommendations.map((rec, idx) => (
                <li key={idx} className="flex items-start gap-2.5 text-xs text-slate-300">
                  <span className="w-5 h-5 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-[10px] shrink-0 mt-0.5">
                    {idx + 1}
                  </span>
                  <span className="leading-relaxed">{rec}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Section F: Action Buttons Footer */}
        <div className="p-4 border-t border-slate-800 bg-slate-900/95 flex items-center justify-between gap-2">
          {onDelete && (
            <button
              onClick={() => onDelete(threat.id)}
              disabled={isActionInProgress}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
            >
              <Trash2 className="w-4 h-4" />
              <span>Delete</span>
            </button>
          )}

          <div className="flex items-center gap-2 ml-auto">
            {onAction && (
              <>
                <button
                  onClick={() => setShowFeedbackModal('SAFE')}
                  disabled={isActionInProgress}
                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-emerald-400 border border-slate-700 transition-colors"
                >
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Mark Safe</span>
                </button>

                <button
                  onClick={() => setShowFeedbackModal('DANGEROUS')}
                  disabled={isActionInProgress}
                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-rose-400 border border-slate-700 transition-colors"
                >
                  <Flag className="w-3.5 h-3.5" />
                  <span>Report Scam</span>
                </button>
              </>
            )}

            <button
              onClick={onClose}
              className="px-4 py-2 rounded-lg text-xs font-semibold bg-cyan-600 hover:bg-cyan-500 text-white transition-colors"
            >
              Done
            </button>
          </div>
        </div>
      </div>

      {/* Interactive Feedback Confirmation Modal */}
      {showFeedbackModal && (
        <div className="fixed inset-0 z-60 bg-black/70 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <h3 className="text-base font-bold text-white flex items-center gap-2">
              {showFeedbackModal === 'SAFE' ? (
                <>
                  <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                  <span>Mark as Safe & Teach Squish</span>
                </>
              ) : (
                <>
                  <ShieldAlert className="w-5 h-5 text-rose-400" />
                  <span>Report Threat & Protect Others</span>
                </>
              )}
            </h3>
            <p className="text-xs text-slate-300 leading-relaxed">
              {showFeedbackModal === 'SAFE'
                ? 'Why do you consider this message safe? Squish will calibrate your profile to prevent false warnings.'
                : 'Help Squish identify scam patterns. Why do you consider this message dangerous?'}
            </p>

            <div className="space-y-2">
              {(showFeedbackModal === 'SAFE'
                ? [
                    'Legitimate message from my bank or service',
                    'I know and trust this sender',
                    'This is a normal personal message',
                    'Standard delivery or transactional notice',
                  ]
                : [
                    'Dangerous scam / phishing attempt',
                    'Suspicious link / unsolicited payment demand',
                    'Impersonating my bank or government agency',
                    'Unsolicited commercial spam',
                  ]
              ).map((option, idx) => (
                <label
                  key={idx}
                  className="flex items-center gap-2.5 p-2.5 rounded-lg bg-slate-800/60 border border-slate-700/60 cursor-pointer hover:bg-slate-800 text-xs text-slate-200"
                >
                  <input
                    type="radio"
                    name="feedbackReason"
                    value={option}
                    checked={selectedFeedbackReason === option}
                    onChange={() => setSelectedFeedbackReason(option)}
                    className="text-cyan-500 focus:ring-cyan-500"
                  />
                  <span>{option}</span>
                </label>
              ))}
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                onClick={() => {
                  setShowFeedbackModal(null);
                  setSelectedFeedbackReason('');
                }}
                className="px-3 py-1.5 text-xs text-slate-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                onClick={async () => {
                  if (onAction) {
                    await onAction(
                      threat.id,
                      showFeedbackModal === 'SAFE' ? 'MARK_SAFE' : 'BLOCK'
                    );
                  }
                  setShowFeedbackModal(null);
                  setSelectedFeedbackReason('');
                  onClose();
                }}
                className={`px-4 py-1.5 rounded-lg text-xs font-bold text-white ${
                  showFeedbackModal === 'SAFE'
                    ? 'bg-emerald-600 hover:bg-emerald-500'
                    : 'bg-rose-600 hover:bg-rose-500'
                }`}
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
